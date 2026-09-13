package minecraft.wrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * <b>Base Runtime Directory resolver</b>
 * <p>
 * Resolves, validates, and creates the per-OS Base Runtime Directory at
 * {@code <user.home>/mclovers}, which is the single source of truth for the
 * wrapper's runtime root. This runs as the very first startup phase (Phase 0)
 * in {@code App.main}, before any artifact is provisioned, so that a stable
 * absolute root is established regardless of the process current working
 * directory.
 * </p>
 * <p>
 * All path work uses {@link java.nio.file}. The directory name is a fixed
 * constant and is intentionally not configurable.
 * </p>
 */
public final class RuntimeDirectory {

    // Fixed runtime folder name under user.home; not configurable.
    static final String DIR_NAME = "mclovers";

    // Static utility; never instantiated.
    private RuntimeDirectory() {
    }

    /**
     * Resolves and prepares the Base Runtime Directory, creating and validating it.
     * <p>
     * Retained no-argument overload. Delegates to {@link #resolveAndPrepare(String)}
     * with the fixed {@link #DIR_NAME} default, so callers that do not supply a name
     * observe the exact refactor01 behavior.
     * </p>
     *
     * @return The absolute, normalized Base Runtime Directory path.
     * @throws RuntimeDirectoryException When the user home is unavailable, the base
     *                                   path conflicts with a non-directory file, the
     *                                   directory cannot be created, or it is not writable.
     */
    public static Path resolveAndPrepare() throws RuntimeDirectoryException {
        return resolveAndPrepare(DIR_NAME);
    }

    /**
     * Resolves and prepares {@code <user.home>/<dirName>}, creating and validating it.
     * <p>
     * Reads {@code System.getProperty("user.home")}, delegates to
     * {@link #resolve(String, String)} for the side-effect-free resolution, then
     * performs validation in a fixed order: create the directory (with any missing
     * parents), verify the result is a directory (never overwriting a conflicting
     * file), and verify the directory is writable. Any failed check aborts so that no
     * downstream phase runs.
     * </p>
     *
     * @param dirName The directory name to root under the user home.
     * @return The absolute, normalized Base Runtime Directory path.
     * @throws RuntimeDirectoryException When the user home is unavailable, {@code dirName}
     *                                   is {@code null} or blank, the base path conflicts
     *                                   with a non-directory file, the directory cannot be
     *                                   created, or it is not writable.
     */
    public static Path resolveAndPrepare(String dirName) throws RuntimeDirectoryException {
        // [Resolve] pure resolution first; no filesystem side effects yet.
        Path base = resolve(System.getProperty("user.home"), dirName);

        /*
         * [Create]
         *
         * Create the base and any missing parents. This is a no-op when the base
         * already exists as a directory, which keeps preparation idempotent.
         */
        try {
            Files.createDirectories(base);                              // create
        } catch (IOException e) {
            throw new RuntimeDirectoryException(
                    "Could not create the base runtime directory: " + base, base, e);
        }

        /*
         * [Is a directory]
         *
         * If the path exists as a non-directory file, abort with a path-conflict
         * message and never delete or overwrite the existing file.
         */
        if (!Files.isDirectory(base)) {                                // directory
            throw new RuntimeDirectoryException(
                    "Base runtime directory path is occupied by a non-directory file: " + base,
                    base, null);
        }

        // [Writable] abort naming the path when the directory cannot be written to.
        if (!Files.isWritable(base)) {                                 // writable
            throw new RuntimeDirectoryException(
                    "Base runtime directory is not writable: " + base, base, null);
        }

        return base;
    }

    /**
     * Resolves {@code <userHome>/mclovers} as an absolute, normalized path.
     * <p>
     * Retained single-argument overload. Delegates to
     * {@link #resolve(String, String)} with the fixed {@link #DIR_NAME} default, so
     * callers that do not supply a name observe the exact refactor01 behavior.
     * </p>
     *
     * @param userHome The user home value to root the directory at.
     * @return The absolute, normalized {@code <userHome>/mclovers} path.
     * @throws RuntimeDirectoryException When {@code userHome} is {@code null} or blank.
     */
    static Path resolve(String userHome) throws RuntimeDirectoryException {
        return resolve(userHome, DIR_NAME);
    }

    /**
     * Resolves {@code <userHome>/<dirName>} as an absolute, normalized path.
     * <p>
     * Performs no filesystem access, which makes it unit-testable against a
     * supplied temporary home and chosen name without mutating the real user home.
     * Because the result derives from {@code userHome} and is normalized to an
     * absolute path, it is identical regardless of the process current working
     * directory, and the {@link Path#resolve(String)} join introduces no hardcoded
     * platform separator.
     * </p>
     *
     * @param userHome The user home value to root the directory at.
     * @param dirName  The directory name to join under the user home.
     * @return The absolute, normalized {@code <userHome>/<dirName>} path.
     * @throws RuntimeDirectoryException When {@code userHome} or {@code dirName} is
     *                                   {@code null} or blank.
     */
    static Path resolve(String userHome, String dirName) throws RuntimeDirectoryException {
        if (userHome == null || userHome.isBlank()) {                  // home
            throw new RuntimeDirectoryException(
                    "Cannot resolve the base runtime directory: user home is unavailable.");
        }
        if (dirName == null || dirName.isBlank()) {                    // name
            throw new RuntimeDirectoryException(
                    "Cannot resolve the base runtime directory: directory name is unavailable.");
        }
        return Paths.get(userHome).resolve(dirName).toAbsolutePath().normalize();
    }
}
