package minecraft.wrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * <b>Service: Server Environment Installer</b>
 * <p>
 * This class is responsible for initializing the server environment on the file system.
 * It ensures that all necessary artifacts (server jar, EULA agreement) are present
 * before the wrapper attempts to launch the server.
 * </p>
 */
public class ServerInstaller {

    private final File serverDir;
    private final String serverJarName;
    private final String eulaFileName;

    /**
     * Creates a new Installer instance.
     *
     * @param serverDir     The working directory for the server (e.g., "minecraft_server").
     * @param serverJarName The name of the server executable jar (e.g., "server.jar").
     * @param eulaFileName  The name of the EULA file (e.g., "eula.txt").
     */
    public ServerInstaller(File serverDir, String serverJarName, String eulaFileName) {
        this.serverDir = serverDir;
        this.serverJarName = serverJarName;
        this.eulaFileName = eulaFileName;
    }

    /**
     * Executes the installation workflow.
     * <p>
     * <b>Steps:</b>
     * <ol>
     *   <li><b>Ensure Directory:</b> Creates the server directory if it doesn't exist.</li>
     *   <li><b>Extract Jar:</b> Copies {@code server.jar} from the bundled resources (inside the wrapper jar)
     *       to the server directory. If the file exists, it is not overwritten to preserve updates.</li>
     *   <li><b>Accept EULA:</b> Automatically creates {@code eula.txt} with {@code eula=true} to
     *       allow headless startups without manual intervention.</li>
     * </ol>
     * </p>
     *
     * @throws IOException If file system operations fail (e.g., disk full, permission denied).
     */
    public void install() throws IOException {
        ensureDirectoryExists();
        ensureServerJarExists();
        ensureEulaAccepted();
    }

    private void ensureDirectoryExists() throws IOException {
        if (!serverDir.exists()) {
            if (!serverDir.mkdirs()) {
                throw new IOException("Failed to create server directory at: " + serverDir.getAbsolutePath());
            }
        }
    }

    private void ensureServerJarExists() throws IOException {
        File serverJar = new File(serverDir, serverJarName);
        if (!serverJar.exists()) {
            System.out.println("Installer: Extracting " + serverJarName + "...");
            
            // We load the jar from the classpath (src/main/resources/server.jar)
            // This allows the wrapper to be a single self-contained unit.
            try (InputStream is = App.class.getResourceAsStream("/" + serverJarName)) {
                if (is == null) {
                    throw new IOException("Critical Error: Resource /" + serverJarName + " not found in classpath. Build may be corrupt.");
                }
                Files.copy(is, serverJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void ensureEulaAccepted() throws IOException {
        File eulaFile = new File(serverDir, eulaFileName);
        // If EULA doesn't exist, we assume the user agrees by running this wrapper
        // and automatically create the file to prevent the server from crashing immediately.
        if (!eulaFile.exists()) {
            System.out.println("Installer: Accepting EULA automatically...");
            try (FileWriter writer = new FileWriter(eulaFile)) {
                writer.write("eula=true\n");
            }
        }
    }
}