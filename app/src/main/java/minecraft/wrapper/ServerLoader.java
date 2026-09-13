package minecraft.wrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * <b>Service: Server Environment Installer</b>
 * <p>
 * This class is responsible for initializing the server environment on the file system.
 * It ensures that the working directory and the EULA agreement are present
 * before the wrapper attempts to launch the server.
 * </p>
 */
public class ServerLoader {

    private final File serverDir;
    private final String eulaFileName;

    /*
     * [Past version]
     * private final String serverJarName;                          // server jar field
     * public ServerLoader(File serverDir, String serverJarName, String eulaFileName) { // ctor
     *
     * Jar extraction moved out of ServerLoader. The Resource_Provisioner now
     * downloads and injects the versioned server jar, so ServerLoader no longer
     * needs the server jar name and its constructor takes only the directory and
     * EULA file name.
     */

    /**
     * Creates a new Loader instance.
     *
     * @param serverDir     The working directory for the server (e.g., "minecraft_server").
     * @param eulaFileName  The name of the EULA file (e.g., "eula.txt").
     */
    public ServerLoader(File serverDir, String eulaFileName) {          // ctor
        this.serverDir = serverDir;
        this.eulaFileName = eulaFileName;
    }

    /**
     * Executes the installation workflow.
     * <p>
     * <b>Steps:</b>
     * <ol>
     *   <li><b>Ensure Directory:</b> Creates the server directory if it doesn't exist.</li>
     *   <li><b>Accept EULA:</b> Automatically creates {@code eula.txt} with {@code eula=true} to
     *       allow headless startups without manual intervention.</li>
     * </ol>
     * </p>
     *
     * @throws IOException If file system operations fail (e.g., disk full, permission denied).
     */
    public void install() throws IOException {
        ensureDirectoryExists();
        ensureEulaAccepted();
    }

    /**
     * Ensures the server directory exists, creating it if necessary.
     *
     * @throws IOException If the directory cannot be created.
     */
    private void ensureDirectoryExists() throws IOException {
        // NIO createDirectories is a no-op when the directory already exists
        // and creates any missing parents in one call.
        Files.createDirectories(serverDir.toPath());                    // NIO
    }

    /*
     * [Past version]
     * private void ensureServerJarExists() throws IOException { ... } // jar extraction
     *
     * Removed the classpath extraction of /server.jar entirely. Jars are no
     * longer bundled as resources; the Resource_Provisioner downloads the
     * latest versioned server jar and injects it into the base directory.
     */

    /**
     * Checks if the EULA file exists, automatically creating and accepting it if missing.
     *
     * @throws IOException If writing the file fails.
     */
    private void ensureEulaAccepted() throws IOException {
        // Separator-free join under the base directory.
        Path eulaFile = serverDir.toPath().resolve(eulaFileName);       // NIO
        // If EULA doesn't exist, we assume the user agrees by running this wrapper
        // and automatically create the file to prevent the server from crashing immediately.
        // An existing EULA is preserved without overwriting.
        if (!Files.exists(eulaFile)) {                                  // NIO
            System.out.println("Loader: Accepting EULA automatically...");
            Files.writeString(eulaFile, "eula=true");                   // NIO
        }
    }
}
