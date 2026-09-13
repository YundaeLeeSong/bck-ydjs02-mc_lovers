package minecraft.wrapper;

import java.io.File;
import java.nio.file.Path;

/**
 * <b>Application Entry Point</b>
 * <p>
 * Orchestrates the lifecycle of the Minecraft Server Wrapper.
 * Handles component installation, configuration generation (Shadow Run),
 * environment customization, and the main server execution loop.
 * </p>
 */
public class App {

    /*
     * [Past version]
     * private static final String SERVER_JAR_NAME = "server.jar";  // server jar literal
     *
     * The server jar name is no longer a compiled literal. The Resource_Provisioner
     * downloads the versioned server jar and returns its Original_Filename at
     * runtime, which is threaded into ServerRunner instead.
     */
    private static final String EULA_FILE_NAME = "eula.txt";

    /**
     * Main method.
     *
     * @param args Command line arguments (unused).
     */
    public static void main(String[] args) {
        try {
            System.out.println("=== Wrapper: Initialization ===");

            /*
             * [Download Config]
             *
             * Load and validate the bundled Download Config first, before Phase 0. A
             * missing resource, malformed properties, or an absent/blank required key
             * throws ProvisioningException here, which the top-level catch below turns
             * into an actionable message and a non-zero exit before any directory is
             * created or any download is attempted. The config is the single source of
             * truth for the runtime directory names, URLs, and recognition patterns.
             */
            DownloadConfig cfg = DownloadConfig.load("download.properties"); // config

            /*
             * [Phase 0: Base Runtime Directory]
             *
             * Resolve, create, and validate the per-OS Base Runtime Directory at
             * <user.home>/<cfg.baseDirName()> before any provisioning. This establishes
             * a stable absolute root regardless of the process current working directory.
             * A failure throws RuntimeDirectoryException, which the top-level catch below
             * turns into a clear message and a non-zero exit with nothing provisioned.
             */
            Path base = RuntimeDirectory.resolveAndPrepare(cfg.baseDirName()); // Phase 0
            File serverDir = base.toFile();                            // resolved base

            /*
             * [Provisioning]
             *
             * Download the versioned server jar and the four required plugin jars into
             * the Staging Directory, recognize them by version-tagged filename, and inject
             * them into the base under their Original_Filename. The returned name is the
             * server jar's Original_Filename, threaded into ServerRunner as the launch jar.
             * A failure throws ProvisioningException, handled by the top-level catch below.
             */
            ResourceProvisioner provisioner =
                    new ResourceProvisioner(base, cfg, new HttpDownloader()); // provisioner
            String serverJarName = provisioner.provision();            // injected jar name

            // --- Phase 1: Installation (Loaders) ---
            // Directory + EULA only; jar extraction and plugin install moved to provisioning.
            ServerLoader serverLoader = new ServerLoader(serverDir, EULA_FILE_NAME);
            serverLoader.install();

            ServerRunner serverRunner = ServerRunner.getInstance(serverDir, serverJarName);
            
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

        } catch (ProvisioningException e) {
            /*
             * [Provisioning failure]
             *
             * Config load, staging, download, recognition, verification, or injection
             * failed. Print an actionable message (naming the offending path when one
             * applies) and exit non-zero. Placed before the RuntimeDirectoryException
             * handler because config load runs before Phase 0.
             */
            Path failedPath = e.getPath();
            if (failedPath != null) {
                System.err.println("Wrapper Error [Provisioning]: " + e.getMessage()
                        + " (path: " + failedPath + ")");
            } else {
                System.err.println("Wrapper Error [Provisioning]: " + e.getMessage());
            }
            System.exit(1);
        } catch (RuntimeDirectoryException e) {
            // [Phase 0 failure] name the base directory or missing user home, then exit non-zero.
            Path failedPath = e.getPath();
            if (failedPath != null) {
                System.err.println("Wrapper Error [Base Directory]: " + e.getMessage()
                        + " (path: " + failedPath + ")");
            } else {
                System.err.println("Wrapper Error [Base Directory]: " + e.getMessage());
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Wrapper Error [Critical]: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
