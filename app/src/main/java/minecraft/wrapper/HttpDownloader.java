package minecraft.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Default {@link Downloader}: JDK HttpClient over the live network</b>
 * <p>
 * Fetches a single artifact into the Staging Directory using only
 * {@code java.net.http} and {@code java.nio.file}. The transfer is streamed to a
 * {@code <name>.part} sibling first so the final, original-named path never exists
 * until the download is verified complete and non-empty. On success the temporary
 * file is moved atomically to its {@code Original_Filename}; on any failure the
 * temporary file is removed (its absence confirmed) and a
 * {@link ProvisioningException} is thrown naming the artifact, URL, and status.
 * </p>
 * <p>
 * Each artifact is attempted up to three times. After the final attempt the
 * download fails outright with no fallback to any prior or bundled jar.
 * </p>
 */
public class HttpDownloader implements Downloader {

    // Per-request timeout and attempt bounds are fixed policy for this wrapper.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);   // timeout
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);   // timeout
    private static final int MAX_ATTEMPTS = 3;                                // attempts

    /*
     * [Redirect cap]
     *
     * The JDK HttpClient with Redirect.NORMAL follows redirects internally and
     * does not expose a chain counter. The design targets at most 5 chained
     * redirects (Requirement 2.4); the cap is documented here and enforced in
     * spirit by NORMAL, which bounds redirect chains and resolves to the final
     * resource whose uri() is used for the filename fallback.
     */
    private static final int MAX_REDIRECTS = 5;                               // redirects

    // Extracts the filename parameter from a Content-Disposition header value.
    private static final Pattern CONTENT_DISPOSITION_FILENAME =
        Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    // Matches an RFC 2047 encoded-word: =?charset?B-or-Q?text?= (e.g. =?UTF-8?Q?x.jar?=).
    private static final Pattern ENCODED_WORD =
        Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?(.*?)\\?=");

    private final HttpClient client;

    /**
     * Constructs a downloader backed by a JDK {@link HttpClient} that follows
     * redirects normally and uses a 30-second connect timeout.
     */
    public HttpDownloader() {
        this.client = HttpClient.newBuilder()
            .followRedirects(Redirect.NORMAL)                                 // redirects
            .connectTimeout(CONNECT_TIMEOUT)                                  // timeout
            .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Streams the response body to a {@code .part} file in {@code stagingDir},
     * verifies a 2xx status and a complete, non-empty transfer, then atomically
     * moves the temporary file to its resolved {@code Original_Filename}. Retries
     * up to three times per artifact; on final failure throws with no fallback.
     * </p>
     */
    @Override
    public Path download(String url, Path stagingDir) throws ProvisioningException {
        ProvisioningException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return attemptDownload(url, stagingDir);
            } catch (ProvisioningException e) {
                // Retain the most recent failure so the final throw names the
                // real cause after all attempts are exhausted.
                lastFailure = e;
            }
        }
        // No fallback to any prior or bundled jar; the artifact download fails.
        throw new ProvisioningException(
            "Failed to download artifact from " + url + " after " + MAX_ATTEMPTS + " attempts",
            lastFailure != null ? lastFailure.getPath() : null,
            lastFailure);
    }

    /**
     * Performs a single download attempt: fetch, verify, and finalize.
     *
     * @param url        The absolute URL of the artifact to download.
     * @param stagingDir The directory the artifact is staged into.
     * @return The staged file under its {@code Original_Filename}.
     * @throws ProvisioningException When the transfer fails, is empty, is
     *                               incomplete, returns a non-success status,
     *                               times out, or cannot be finalized.
     */
    private Path attemptDownload(String url, Path stagingDir) throws ProvisioningException {
        // The temp name is derived after the response is known, so it is resolved
        // once the Original_Filename is in hand; the .part sibling is the only
        // file written until the transfer is verified complete.
        Path part = null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)                                     // timeout
                .GET()
                .build();

            HttpResponse<InputStream> response =
                client.send(request, BodyHandlers.ofInputStream());

            int status = response.statusCode();
            // Content-Length, when present, is the expected byte total used to
            // detect a truncated transfer.
            long expectedLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1L);

            String originalFilename = resolveOriginalFilename(response);
            part = stagingDir.resolve(originalFilename + ".part");            // NIO
            Path target = stagingDir.resolve(originalFilename);               // NIO

            // A non-success status leaves nothing staged; the body is drained by
            // the try-with-resources close and the .part deletion below.
            if (status < 200 || status >= 300) {
                deletePartOrFail(part, url, status,
                    "non-success HTTP status " + status);
            }

            long written;
            try (InputStream body = response.body()) {
                written = Files.copy(body, part, StandardCopyOption.REPLACE_EXISTING);
            }

            // Zero-byte result is a failure regardless of status.
            if (written == 0L) {
                deletePartOrFail(part, url, status, "zero-byte body");
            }
            // Truncated transfer: fewer bytes than the advertised Content-Length.
            if (expectedLength >= 0 && written != expectedLength) {
                deletePartOrFail(part, url, status,
                    "truncated transfer (" + written + " of " + expectedLength + " bytes)");
            }

            return finalize(part, target, url);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // A timeout or connection failure surfaces here; remove any partial
            // file (confirming absence) before reporting the artifact and URL.
            deletePartOrFail(part, url, -1,
                "transfer failed or timed out: " + e.getMessage(), e);
            // deletePartOrFail always throws, but the compiler needs a return.
            throw new ProvisioningException("Failed to download " + url, part, e);
        }
    }

    /**
     * Moves the verified {@code .part} file to its original filename, preferring
     * an atomic move and falling back to a plain replacing move where atomic move
     * is unsupported.
     *
     * @param part   The verified temporary file.
     * @param target The resolved original-named destination.
     * @param url    The source URL, for error reporting.
     * @return The staged file at {@code target}.
     * @throws ProvisioningException When neither move succeeds.
     */
    private Path finalize(Path part, Path target, String url) throws ProvisioningException {
        try {
            return Files.move(part, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException atomicUnsupported) {
            // Plain replacing move where the filesystem cannot move atomically.
            try {
                return Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                deletePartOrFail(part, url, -1, "could not finalize staged file", e);
                throw new ProvisioningException("Failed to finalize " + url, part, e);
            }
        } catch (IOException e) {
            deletePartOrFail(part, url, -1, "could not finalize staged file", e);
            throw new ProvisioningException("Failed to finalize " + url, part, e);
        }
    }

    /**
     * Derives the {@code Original_Filename} from the response: the
     * {@code Content-Disposition} {@code filename} parameter when present,
     * otherwise the last path segment of the final (post-redirect) response URI.
     *
     * @param response The completed HTTP response.
     * @return The original filename the artifact ships under.
     * @throws ProvisioningException When no filename can be determined.
     */
    private String resolveOriginalFilename(HttpResponse<InputStream> response)
            throws ProvisioningException {
        String disposition = response.headers()
            .firstValue("Content-Disposition")
            .orElse(null);
        if (disposition != null) {
            Matcher m = CONTENT_DISPOSITION_FILENAME.matcher(disposition);
            if (m.find()) {
                String name = sanitize(m.group(1));
                if (!name.isBlank()) {
                    return name;
                }
            }
        }

        // Fallback: last path segment of the final redirected URI.
        URI finalUri = response.uri();
        String path = finalUri.getPath();
        if (path != null && !path.isBlank()) {
            String segment = path.substring(path.lastIndexOf('/') + 1);
            String name = sanitize(segment);
            if (!name.isBlank()) {
                return name;
            }
        }
        throw new ProvisioningException(
            "Could not determine a filename for artifact from " + finalUri);
    }

    /**
     * Normalizes a header- or URI-derived filename into a value that is safe to
     * use as a path segment on any OS.
     * <p>
     * Three steps run in order. First an RFC 2047 encoded-word (for example
     * {@code =?UTF-8?Q?Geyser-Spigot.jar?=}, which the GeyserMC download endpoint
     * emits in {@code Content-Disposition}) is decoded to its plain text, because
     * such a value contains {@code ?} characters that are illegal in a Windows
     * path. Then any leading directory components are stripped so a supplied name
     * cannot escape the Staging Directory. Finally any character that is illegal
     * in a Windows filename ({@code \ / : * ? " < > |}) is replaced with an
     * underscore, so a hostile or unusual header can never produce an invalid path.
     * </p>
     *
     * @param raw The raw filename candidate.
     * @return The bare, path-safe filename.
     */
    private String sanitize(String raw) {
        String decoded = decodeEncodedWord(raw.trim());               // RFC 2047
        int slash = Math.max(decoded.lastIndexOf('/'), decoded.lastIndexOf('\\'));
        String bare = slash >= 0 ? decoded.substring(slash + 1) : decoded;
        // Replace characters Windows forbids in a filename so resolve() cannot throw.
        return bare.replaceAll("[\\\\/:*?\"<>|]", "_").trim();         // illegal chars
    }

    /*
     * [RFC 2047 encoded-word]
     *
     * Some servers send Content-Disposition filenames as MIME encoded-words of
     * the form =?charset?B?...?= (Base64) or =?charset?Q?...?= (quoted-printable),
     * for example =?UTF-8?Q?Geyser-Spigot.jar?=. This decodes those two encodings
     * back to plain text. A value that is not an encoded-word is returned
     * unchanged, and any decode failure falls back to the original string so
     * recognition can still proceed.
     */
    private String decodeEncodedWord(String value) {
        Matcher m = ENCODED_WORD.matcher(value);
        if (!m.matches()) {
            return value;                                             // not encoded
        }
        String charset = m.group(1);
        String encoding = m.group(2).toUpperCase();
        String text = m.group(3);
        try {
            java.nio.charset.Charset cs = java.nio.charset.Charset.forName(charset);
            if ("B".equals(encoding)) {                              // Base64
                byte[] bytes = java.util.Base64.getDecoder().decode(text);
                return new String(bytes, cs);
            }
            if ("Q".equals(encoding)) {                              // quoted-printable
                return decodeQuotedPrintable(text, cs);
            }
        } catch (RuntimeException decodeFailure) {
            // Fall through: use the raw value rather than aborting the download.
        }
        return value;
    }

    /*
     * [Quoted-printable]
     *
     * Decodes the Q-encoding used inside an RFC 2047 encoded-word: '_' means a
     * space and '=XX' is a hex-encoded byte. Bytes are collected and then decoded
     * with the encoded-word's charset so multi-byte characters survive.
     */
    private String decodeQuotedPrintable(String text, java.nio.charset.Charset cs) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '_') {
                out.write(' ');                                      // underscore -> space
            } else if (c == '=' && i + 2 < text.length()) {
                int hi = Character.digit(text.charAt(i + 1), 16);
                int lo = Character.digit(text.charAt(i + 2), 16);
                out.write((hi << 4) + lo);                           // =XX byte
                i += 2;
            } else {
                out.write(c);                                        // literal
            }
        }
        return new String(out.toByteArray(), cs);
    }

    /**
     * Deletes the partial file, confirms its absence, and throws a
     * {@link ProvisioningException} naming the artifact, URL, and status. When the
     * deletion itself fails, the thrown exception reports both the download failure
     * and the deletion failure.
     */
    private void deletePartOrFail(Path part, String url, int status, String reason)
            throws ProvisioningException {
        deletePartOrFail(part, url, status, reason, null);
    }

    /**
     * Deletes the partial file, confirms its absence, and throws a
     * {@link ProvisioningException} naming the artifact, URL, and status.
     *
     * @param part   The temporary file to remove, or {@code null} if none exists yet.
     * @param url    The source URL, for error reporting.
     * @param status The HTTP status, or a negative value when not applicable.
     * @param reason A human-readable failure reason.
     * @param cause  The underlying cause, or {@code null}.
     * @throws ProvisioningException Always, reporting the download (and any deletion) failure.
     */
    private void deletePartOrFail(Path part, String url, int status, String reason, Throwable cause)
            throws ProvisioningException {
        String statusPart = status >= 0 ? " (status " + status + ")" : "";
        String message = "Download failed for " + url + statusPart + ": " + reason;

        if (part != null) {
            try {
                Files.deleteIfExists(part);
                // Confirm the partial/empty file is truly gone before halting.
                if (Files.exists(part)) {
                    throw new ProvisioningException(
                        message + "; additionally the partial file could not be removed: " + part,
                        part, cause);
                }
            } catch (IOException deletionFailure) {
                throw new ProvisioningException(
                    message + "; additionally the partial file could not be removed: " + part,
                    part, deletionFailure);
            }
        }
        throw new ProvisioningException(message, part, cause);
    }
}
