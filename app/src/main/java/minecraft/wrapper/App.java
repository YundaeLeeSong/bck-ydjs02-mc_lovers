package minecraft.wrapper;

import java.io.File;

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
            
            ServerRunner serverRunner = ServerRunner.getInstance(serverDir, SERVER_JAR_NAME);
            
            // Register centralized Shutdown Hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // 1. Stop the server first (blocks until process is dead)
                serverRunner.terminate();
                
                // 2. Synchronize output and pause
                synchronized (serverRunner) {
                    System.out.println("\n=== Wrapper: Cleanup & Shutdown ===");
                    System.out.println("It is all cleaned up.");
                    
                    try {
                        // Check if we can interact with the user
                        if (System.console() != null || System.in.available() >= 0) {
                             System.out.println("Press any key (or wait 5s) to exit this session...");
                             
                             // Simple non-blocking wait loop or timed read simulation
                             long start = System.currentTimeMillis();
                             while (System.currentTimeMillis() - start < 5000) {
                                 if (System.in.available() > 0) {
                                     System.in.read();
                                     break;
                                 }
                                 Thread.sleep(100);
                             }
                        } else {
                            System.out.println("Non-interactive mode. Exiting in 3s...");
                            Thread.sleep(3000);
                        }
                    } catch (Exception e) {
                        System.out.println("(Input stream closed. Exiting...)");
                    }
                }
            }, "Wrapper-Cleanup-Hook"));

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
            
            boolean enableGui = Boolean.parseBoolean(System.getenv().getOrDefault("MC_GUI", "true"));
            

            
            int exitCode = 0;
            try {
                // Actual Run (blocks until server exits)
                // exitCode = serverRunner.execute(enableGui);
                exitCode = serverRunner.execute(false); // make it CLI for OCI
                
                // Synchronize the exit message so it doesn't mix with the hook
                synchronized (serverRunner) {
                    System.out.println("Wrapper: Server exited with code: " + exitCode);
                }
            } catch (Exception e) {
                synchronized (serverRunner) {
                    System.err.println("Wrapper: Server crashed: " + e.getMessage());
                    e.printStackTrace();
                }
                exitCode = 1;
            }
            
            // Explicit exit calls the shutdown hook naturally
            System.exit(exitCode);

        } catch (Exception e) {
            System.err.println("Wrapper Error [Critical]: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
