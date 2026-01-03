package minecraft.wrapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * <b>Service: Velocity Proxy Installer</b>
 * <p>
 * Responsible for checking and installing the Velocity Proxy environment.
 * <br>
 * <b>Implementation Strategy:</b>
 * Similar to the {@link ServerInstaller}, this class expects the Velocity jar and its plugins
 * to be bundled within the application's resources (classpath). It extracts them to the
 * working directory if they are not already present.
 * </p>
 */
public class VelocityInstaller {
    private final File proxyDir;
    
    private static final String VELOCITY_JAR = "velocity.jar";
    private static final String GEYSER_JAR_NAME = "Geyser-Velocity.jar";
    private static final String FLOODGATE_JAR_NAME = "floodgate-velocity.jar";
    private static final String GEYSER_RESOURCE = "/plugins/" + GEYSER_JAR_NAME;
    private static final String FLOODGATE_RESOURCE = "/plugins/" + FLOODGATE_JAR_NAME;

    /**
     * Creates a new VelocityInstaller.
     *
     * @param proxyDir The directory where the proxy will be installed.
     */
    public VelocityInstaller(File proxyDir) {
        this.proxyDir = proxyDir;
    }

    /**
     * Executes the installation workflow.
     * <p>
     * 1. Ensures proxy directory and plugins directory exist.
     * 2. Checks for {@code velocity.jar} and extracts from resources if missing.
     * 3. Checks for {@code Geyser-Velocity.jar} and extracts from resources if missing.
     * 4. Checks for {@code floodgate-velocity.jar} and extracts from resources if missing.
     * </p>
     *
     * @throws IOException If file system operations fail or resources are missing.
     */
    public void install() throws IOException {
        if (!proxyDir.exists()) {
             proxyDir.mkdirs();
        }
        File pluginsDir = new File(proxyDir, "plugins");
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
        }

        // We check for resources first to fail fast if the build is invalid
        boolean velocityInRes = App.class.getResource("/" + VELOCITY_JAR) != null;
        boolean geyserInRes = App.class.getResource(GEYSER_RESOURCE) != null;
        boolean floodgateInRes = App.class.getResource(FLOODGATE_RESOURCE) != null;
        
        File velocityFile = new File(proxyDir, VELOCITY_JAR);
        if (!velocityFile.exists()) {
            if (!velocityInRes) {
                InstallationReporter.printMissingResourcesReport(true, false, false);
                throw new IOException("Missing bundled resource: " + VELOCITY_JAR);
            }
            extractResource("/" + VELOCITY_JAR, velocityFile);
        }

        File geyserFile = new File(pluginsDir, GEYSER_JAR_NAME);
        if (!geyserFile.exists()) {
             if (!geyserInRes) {
                InstallationReporter.printMissingResourcesReport(false, true, false);
                throw new IOException("Missing bundled resource: " + GEYSER_RESOURCE);
            }
            extractResource(GEYSER_RESOURCE, geyserFile);
        }

        File floodgateFile = new File(pluginsDir, FLOODGATE_JAR_NAME);
        if (!floodgateFile.exists()) {
             if (!floodgateInRes) {
                InstallationReporter.printMissingResourcesReport(false, false, true);
                throw new IOException("Missing bundled resource: " + FLOODGATE_RESOURCE);
            }
            extractResource(FLOODGATE_RESOURCE, floodgateFile);
        }
        
        System.out.println("VelocityInstaller: Verified installation.");
    }

    private void extractResource(String resourcePath, File target) throws IOException {
        System.out.println("VelocityInstaller: Extracting " + resourcePath + "...");
        try (InputStream is = App.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}