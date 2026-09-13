package minecraft.wrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * <b>Resource Provisioner</b>
 * <p>
 * Orchestrates the per-run provisioning of the server jar and the four required
 * plugin jars over the internet. It runs immediately after Phase 0
 * ({@code RuntimeDirectory.resolveAndPrepare}) and before every downstream phase,
 * resolving a dedicated Staging Directory at {@code <user.home>/<staging-dir-name>},
 * wiping and recreating it, downloading the latest artifacts into it, recognizing
 * them by version-tagged filename, and injecting them into the Base Runtime
 * Directory under their original downloaded filenames.
 * </p>
 * <p>
 * The staging directory name is supplied by the {@link DownloadConfig} this
 * provisioner holds ({@code config.stagingDirName()}, documented default
 * {@code resources}); there is intentionally no compiled directory-name constant,
 * so no directory-name literal is baked into this class. All path work uses
 * {@link java.nio.file}, and path joins use {@link Path#resolve(String)} so no
 * hardcoded platform separator appears.
 * </p>
 */
public final class ResourceProvisioner {

    // Wipe-and-recreate must finish within this bound or the run aborts.
    private static final long WIPE_TIMEOUT_NANOS = 30L * 1_000_000_000L;

    private final Path baseDir;          // resolved Base Runtime Directory
    private final DownloadConfig config; // supplies staging dir name, URLs, patterns
    private final Downloader downloader; // HTTP seam, HttpClient-backed by default

    /**
     * Creates a provisioner bound to a resolved base directory, config, and download seam.
     * <p>
     * The staging directory name is not passed here; it is read from
     * {@code config.stagingDirName()} when staging is resolved, keeping the config the
     * single source of truth for the name.
     * </p>
     *
     * @param baseDir    The resolved, absolute Base Runtime Directory (injection target root).
     * @param config     The loaded Download Config supplying the staging name, URLs, and patterns.
     * @param downloader The download seam used to fetch each artifact into staging.
     */
    public ResourceProvisioner(Path baseDir, DownloadConfig config, Downloader downloader) {
        this.baseDir = baseDir;
        this.config = config;
        this.downloader = downloader;
    }

    /**
     * Runs the full download-recognize-verify-inject pipeline, returning the injected
     * server jar's {@code Original_Filename}.
     * <p>
     * The pipeline resolves staging under {@code config.stagingDirName()}, wipes and
     * recreates it, downloads the server jar and the four required plugin jars via the
     * injected {@link Downloader} using URLs from the config, recognizes and classifies
     * the staged jars with {@link JarRecognizer}, re-verifies each recognized file
     * exists and is non-empty immediately before its copy, then injects: the server jar
     * is copied to {@code base/<originalServerFilename>} and each plugin to
     * {@code base/plugins/<originalPluginFilename>} under the source's own filename with
     * {@link StandardCopyOption#REPLACE_EXISTING}, creating {@code base/plugins} first if
     * absent.
     * </p>
     * <p>
     * Failure is fatal and clean. On any failure the pipeline removes the partial or
     * unrecognized artifacts it added to both the Staging Directory and the base
     * (deleting only the base destinations this run wrote, so pre-existing base contents
     * are preserved), launches no child process, and throws {@link ProvisioningException}:
     * a missing recognized source names it, a pre-injection empty file names the artifact,
     * and a copy failure reports the source and destination.
     * </p>
     *
     * @return The injected server jar's {@code Original_Filename}
     *         ({@code recognizedServerJar.getFileName().toString()}), which {@code App}
     *         hands to {@code ServerRunner} as the launch jar.
     * @throws ProvisioningException When staging, downloading, recognition, pre-injection
     *                               verification, or injection fails; partial artifacts
     *                               are cleaned from both staging and base first.
     */
    public String provision() throws ProvisioningException {
        // [Wipe + recreate] a fresh, empty staging directory for this run.
        Path staging = wipeAndRecreateStaging();

        // Base destinations written this run; used to clean only what we added on failure.
        List<Path> injected = new ArrayList<>();

        try {
            /*
             * [Download]
             *
             * Fetch the server jar and each of the four plugin jars into staging. The
             * downloader stages each under its Original_Filename after verifying the
             * transfer is complete and non-empty. The staged paths feed recognition.
             */
            List<Path> staged = new ArrayList<>();                     // staged jars
            staged.add(downloader.download(config.serverUrl(), staging));
            for (PluginId id : PluginId.values()) {
                staged.add(downloader.download(config.pluginUrl(id), staging));
            }

            /*
             * [Recognize]
             *
             * Classify the staged jars by version-tagged filename. The recognizer
             * throws naming any artifact whose pattern matches nothing, leaving the
             * staged files unmodified.
             */
            RecognitionPatterns patterns = buildPatterns();            // from config
            RecognitionResult recognized = new JarRecognizer(patterns).recognize(staged);

            /*
             * [Verify + inject: server]
             *
             * Re-verify the recognized server jar is present and non-empty immediately
             * before copying it, then copy it into the base under its Original_Filename,
             * overwriting any existing file of that name.
             */
            Path serverSource = recognized.serverJar();
            verifyNonEmpty(serverSource, "server jar");
            Path serverDest = baseDir.resolve(serverSource.getFileName().toString());
            copy(serverSource, serverDest, injected);

            /*
             * [Verify + inject: plugins]
             *
             * Create base/plugins if absent, then for each plugin re-verify non-empty
             * and copy it under its own filename, overwriting an existing file of that
             * name.
             */
            Path basePlugins = baseDir.resolve("plugins");             // plugin root
            try {
                Files.createDirectories(basePlugins);                  // ensure present
            } catch (IOException e) {
                throw new ProvisioningException(
                        "Could not create the base plugins directory: " + basePlugins,
                        basePlugins, e);
            }
            for (Map.Entry<PluginId, Path> entry : recognized.pluginJars().entrySet()) {
                Path pluginSource = entry.getValue();
                verifyNonEmpty(pluginSource, "plugin jar (" + entry.getKey() + ")");
                Path pluginDest = basePlugins.resolve(pluginSource.getFileName().toString());
                copy(pluginSource, pluginDest, injected);
            }

            // The injected server jar's Original_Filename is the launch jar name.
            return serverSource.getFileName().toString();
        } catch (ProvisioningException e) {
            // [Clean on failure] remove what this run added; keep pre-existing base files.
            cleanPartialArtifacts(staging, injected);
            throw e;
        }
    }

    /*
     * [Recognition patterns from config]
     *
     * Assembles the server pattern and the per-plugin patterns the config supplies into
     * the RecognitionPatterns the JarRecognizer consumes, keeping the config the single
     * source of truth for every recognition regex.
     */
    private RecognitionPatterns buildPatterns() {
        Map<PluginId, java.util.regex.Pattern> pluginPatterns = new EnumMap<>(PluginId.class);
        for (PluginId id : PluginId.values()) {
            pluginPatterns.put(id, config.pluginPattern(id));
        }
        return new RecognitionPatterns(config.serverPattern(), pluginPatterns);
    }

    /**
     * Verifies a recognized source file exists and is non-empty immediately before copy.
     *
     * @param source The recognized staged file to check.
     * @param label  A human-readable artifact label used in the error message.
     * @throws ProvisioningException When the source is missing (naming it) or empty
     *                               (naming the artifact).
     */
    private static void verifyNonEmpty(Path source, String label) throws ProvisioningException {
        if (!Files.exists(source)) {                                   // missing source
            throw new ProvisioningException(
                    "Recognized " + label + " source is missing before injection: " + source,
                    source, null);
        }
        try {
            if (Files.size(source) <= 0L) {                            // empty artifact
                throw new ProvisioningException(
                        "Recognized " + label + " is empty before injection: " + source,
                        source, null);
            }
        } catch (IOException e) {
            throw new ProvisioningException(
                    "Could not read the size of the recognized " + label + ": " + source,
                    source, e);
        }
    }

    /**
     * Copies a recognized source into the base, recording the destination for cleanup.
     * <p>
     * The destination is recorded before the copy is attempted so a failed or partial
     * copy is still cleaned from the base on failure. The copy overwrites any existing
     * file of the same name.
     * </p>
     *
     * @param source   The recognized staged source file.
     * @param dest     The base destination path (under the source's own filename).
     * @param injected The list of base destinations written this run.
     * @throws ProvisioningException When the copy fails, reporting the source and destination.
     */
    private static void copy(Path source, Path dest, List<Path> injected)
            throws ProvisioningException {
        injected.add(dest);                                            // track for cleanup
        try {
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ProvisioningException(
                    "Could not inject artifact from " + source + " to " + dest, dest, e);
        }
    }

    /*
     * [Clean partial artifacts]
     *
     * On failure, delete only what this run added: the whole staging tree (every
     * download this run staged lives there) and each base destination this run wrote
     * (tracked in injected). Pre-existing base contents are never touched, since only
     * this run's destinations are deleted. Cleanup itself is best-effort and swallows
     * IOException so the original ProvisioningException remains the reported failure.
     */
    private void cleanPartialArtifacts(Path staging, List<Path> injected) {
        // [Base] remove only the destinations this run wrote.
        for (Path dest : injected) {
            try {
                Files.deleteIfExists(dest);                            // base artifact
            } catch (IOException ignored) {
                // Best-effort: keep reporting the original provisioning failure.
            }
        }

        // [Staging] remove the whole staging tree this run's downloads landed in.
        if (staging != null && Files.exists(staging)) {
            try (Stream<Path> walk = Files.walk(staging)) {
                List<Path> entries = walk
                        .sorted(Comparator.reverseOrder())             // depth-first
                        .toList();
                for (Path entry : entries) {
                    Files.deleteIfExists(entry);                       // staging entry
                }
            } catch (IOException ignored) {
                // Best-effort: keep reporting the original provisioning failure.
            }
        }
    }

    /**
     * Resolves {@code <userHome>/<stagingDirName>} as an absolute, normalized path.
     * <p>
     * Mirrors {@code RuntimeDirectory.resolve}: it performs no filesystem access, which
     * makes it unit-testable against a supplied temporary home and chosen name. Because
     * the result derives from {@code userHome} and is normalized to an absolute path, it
     * is identical regardless of the process current working directory, and the
     * {@link Path#resolve(String)} join introduces no hardcoded platform separator.
     * </p>
     *
     * @param userHome       The user home value to root the staging directory at.
     * @param stagingDirName The staging directory name to join under the user home.
     * @return The absolute, normalized {@code <userHome>/<stagingDirName>} path.
     * @throws ProvisioningException When {@code userHome} or {@code stagingDirName} is
     *                               {@code null} or blank.
     */
    static Path resolveStaging(String userHome, String stagingDirName) throws ProvisioningException {
        if (userHome == null || userHome.isBlank()) {                  // home
            throw new ProvisioningException(
                    "Cannot resolve the staging directory: user home is unavailable.");
        }
        if (stagingDirName == null || stagingDirName.isBlank()) {      // name
            throw new ProvisioningException(
                    "Cannot resolve the staging directory: staging directory name is unavailable.");
        }
        return Paths.get(userHome).resolve(stagingDirName).toAbsolutePath().normalize();
    }

    /**
     * Recursively wipes and recreates the Staging Directory, returning the empty directory.
     * <p>
     * Resolves staging from {@code System.getProperty("user.home")} and
     * {@code config.stagingDirName()}, then deletes the existing tree depth-first and
     * recreates it empty. A missing staging directory makes the delete a no-op. The
     * whole operation is guarded by a 30-second deadline: an entry that cannot be
     * deleted (for example a file locked or in use by another process) aborts naming the
     * path and cause, and exceeding the deadline aborts naming the path and the timeout.
     * On successful return the Staging Directory exists and contains zero entries.
     * </p>
     *
     * @return The absolute, normalized, freshly recreated (empty) Staging Directory path.
     * @throws ProvisioningException When staging cannot be resolved, an entry cannot be
     *                               deleted, recreation fails, or the operation exceeds
     *                               the 30-second deadline.
     */
    Path wipeAndRecreateStaging() throws ProvisioningException {
        Path staging = resolveStaging(System.getProperty("user.home"), config.stagingDirName());

        // [Deadline] a single bound covers the entire wipe-and-recreate.
        long deadline = System.nanoTime() + WIPE_TIMEOUT_NANOS;

        /*
         * [Wipe]
         *
         * Walk the tree depth-first (children before parents) by reverse-sorting the
         * walk, so each directory is emptied before it is deleted. deleteIfExists makes
         * a concurrently vanished entry harmless, and a missing staging root short-
         * circuits to a no-op. The deadline is checked before each delete so a slow or
         * stuck tree cannot exceed the 30-second bound. A delete failure (for example a
         * locked or in-use entry) aborts naming the path and the cause.
         */
        if (Files.exists(staging)) {                                   // present
            try (Stream<Path> walk = Files.walk(staging)) {
                List<Path> entries = walk
                        .sorted(Comparator.reverseOrder())             // depth-first
                        .toList();
                for (Path entry : entries) {
                    checkDeadline(deadline, staging);                  // timeout
                    try {
                        Files.deleteIfExists(entry);                   // delete
                    } catch (IOException e) {
                        throw new ProvisioningException(
                                "Could not delete a staging entry (it may be locked or in use): "
                                        + entry,
                                entry, e);
                    }
                }
            } catch (IOException e) {
                throw new ProvisioningException(
                        "Could not walk the staging directory for wiping: " + staging, staging, e);
            }
        }

        // [Deadline] re-check before recreate so a slow wipe still bounds the whole op.
        checkDeadline(deadline, staging);                              // timeout

        /*
         * [Recreate]
         *
         * Create the staging directory and any missing parents so the recreated
         * directory exists and is empty for the download step.
         */
        try {
            Files.createDirectories(staging);                          // recreate
        } catch (IOException e) {
            throw new ProvisioningException(
                    "Could not recreate the staging directory: " + staging, staging, e);
        }

        return staging;
    }

    /**
     * Aborts when the 30-second wipe-and-recreate deadline has been reached.
     *
     * @param deadline The {@link System#nanoTime()} value at which the operation times out.
     * @param staging  The staging path reported in the timeout error.
     * @throws ProvisioningException When the current time is at or past {@code deadline}.
     */
    private static void checkDeadline(long deadline, Path staging) throws ProvisioningException {
        if (System.nanoTime() >= deadline) {                           // timeout
            throw new ProvisioningException(
                    "Wipe-and-recreate of the staging directory exceeded the 30-second timeout: "
                            + staging,
                    staging, null);
        }
    }
}
