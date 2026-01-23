package minecraft.wrapper;

import java.io.File;
import sun.misc.Signal;

/**
 * <b>Application Entry Point</b>
 * <p>
 * Orchestrates the lifecycle of the Minecraft Server Wrapper.
 * Handles component installation, configuration generation (Shadow Run),
 * environment customization, and the main server execution loop.
 * </p>
 */
public class App {

    private static final String SERVER_DIR_NAME = "minecraft_server";
    private static final String SERVER_JAR_NAME = "server.jar";
    private static final String EULA_FILE_NAME = "eula.txt";

    /**
     * Main method.
     *
     * @param args Command line arguments (unused).
     */
    public static void main(String[] args) {
        File serverDir = new File(SERVER_DIR_NAME);
        
        try {
            System.out.println("=== Wrapper: Initialization ===");

            // --- Phase 1: Installation (Loaders) ---
            // 1. Server Jar
            ServerLoader serverLoader = new ServerLoader(serverDir, SERVER_JAR_NAME, EULA_FILE_NAME);
            serverLoader.install();

            // 2. Plugins
            PluginsLoader pluginsLoader = new PluginsLoader(serverDir);
            pluginsLoader.install();
            
            ServerRunner serverRunner = new ServerRunner(serverDir, SERVER_JAR_NAME);

            // --- Phase 2: Configuration Generation (Shadow Run) ---
            // Only run if configs are missing
            File serverProps = new File(serverDir, "server.properties");
            File geyserConfigFile = new File(serverDir, "plugins/Geyser-Spigot/config.yml");
            
            if (!serverProps.exists() || !geyserConfigFile.exists()) {
                System.out.println("=== Wrapper: Generating Configurations (First Run) ===");
                // Run server until initialized, then stop immediately.
                serverRunner.generateConfigs();
            } else {
                System.out.println("=== Wrapper: Configurations Found (Skipping Shadow Run) ===");
            }

            // --- Phase 3: Configuration Modification ---
            System.out.println("=== Wrapper: Applying Configuration Overrides ===");
            
            // Server Properties (Purpur/Paper/Spigot)
            // Now strictly modifies existing file from Phase 2
            File propertiesFile = new File(serverDir, "server.properties");
            ServerConfig serverConfig = new ServerConfig(propertiesFile);
            serverConfig.load();
            serverConfig.applyEnvironmentVariables(); 
            // Enforce port 25565
            serverConfig.setProperty("server-port", "25565");
            serverConfig.setProperty("online-mode", "true");
            serverConfig.save();
            
            // Geyser Config
            // Now strictly modifies existing file from Phase 2
            GeyserConfig geyserConfig = new GeyserConfig(serverDir);
            geyserConfig.configure();

            // --- Phase 4: Execution ---
            NetworkReporter.printReport();
            
            // Intercept Ctrl+C (SIGINT)
            try {
                Signal.handle(new Signal("INT"), signal -> { 
                     System.out.println("\nWrapper: Caught Ctrl+C. Waiting for server to shut down...");
                });
            } catch (Throwable t) {
                System.out.println("Wrapper: Warning - Could not register Signal Handler (" + t.getMessage() + ")");
            }

            boolean enableGui = Boolean.parseBoolean(System.getenv().getOrDefault("MC_GUI", "true"));
            
            int exitCode = 0;
            try {
                // Actual Run (Thread 2)
                exitCode = serverRunner.start(enableGui);
                System.out.println("Wrapper: Server exited with code: " + exitCode);
            } catch (Exception e) {
                System.err.println("Wrapper: Server crashed: " + e.getMessage());
                e.printStackTrace();
                exitCode = 1;
            }
            
            // --- Phase 5: Clean Cleanup Prompt ---
            System.out.println("\nIt is all cleaned up, press any key to safely exit this server session..!");
            try {
                System.in.read();
            } catch (Exception e) {
                // Ignore
            }
            
            System.exit(exitCode);

        } catch (Exception e) {
            System.err.println("Wrapper Error [Critical]: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
