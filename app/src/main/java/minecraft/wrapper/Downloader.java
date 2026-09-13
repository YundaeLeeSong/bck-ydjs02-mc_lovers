package minecraft.wrapper;

import java.nio.file.Path;

/**
 * <b>Download seam</b>
 * <p>
 * Abstracts fetching a single remote artifact (the server jar or a plugin jar)
 * into the Staging Directory. It is the injectable boundary between the
 * provisioning orchestration and the network, so that {@code ResourceProvisioner}
 * can be driven with the real {@code HttpDownloader} in production and with a fake
 * or a local {@code com.sun.net.httpserver.HttpServer} under test, never touching
 * the real user home or the live network during a test run.
 * </p>
 */
public interface Downloader {

    /**
     * Downloads the artifact at {@code url} into {@code stagingDir}, returning the
     * staged file under its original filename.
     * <p>
     * The contract an implementation must honor is as follows.
     * </p>
     * <ol>
     * <li><b>Stage under a temporary name.</b> The transfer is written first to a
     * temporary file within {@code stagingDir} (for example a {@code .part}
     * sibling), so that the final, original-named path never exists until the
     * transfer is known to be complete.</li>
     * <li><b>Verify complete and non-empty.</b> The transfer is checked for a
     * successful, complete download that produced a non-empty file. A zero-byte
     * result, a truncated transfer, a non-success response, or a timeout is treated
     * as a failure: the temporary file is removed and a {@link ProvisioningException}
     * is thrown naming the artifact or URL, with nothing left staged under the
     * original name.</li>
     * <li><b>Move atomically to the original filename.</b> On success the temporary
     * file is moved atomically to its {@code Original_Filename}, which is derived
     * from the response: the {@code Content-Disposition} {@code filename} parameter
     * when present, otherwise the last path segment of the final (post-redirect)
     * response URI.</li>
     * <li><b>Return the staged path.</b> The returned {@link Path} is the staged
     * file at its original filename inside {@code stagingDir}.</li>
     * </ol>
     *
     * @param url        The absolute URL of the artifact to download.
     * @param stagingDir The directory the artifact is staged into.
     * @return The staged file's absolute {@link Path} under its {@code Original_Filename}.
     * @throws ProvisioningException When the transfer fails, is empty, is incomplete,
     *                               returns a non-success status, times out, or the
     *                               staged file cannot be finalized under its original
     *                               name.
     */
    Path download(String url, Path stagingDir) throws ProvisioningException;
}
