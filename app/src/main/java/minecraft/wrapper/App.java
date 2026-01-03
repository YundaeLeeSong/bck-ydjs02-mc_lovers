package minecraft.wrapper;

import java.io.File;
import java.io.IOException;

/**
 * <b>Application Entry Point: The Orchestrator</b>
 * <p>
 * The {@code App} class serves as the <b>Orchestrator</b> for the dual-process architecture.
 * It manages the lifecycle of:
 * <ol>
 *   <li><b>Velocity Proxy</b> (Frontend): Listens on 25565 (Java) and 19132 (Bedrock).
 *       Runs as a background process.</li>
 *   <li><b>Vanilla Server</b> (Backend): Listens on 25566 (Internal).
 *       Runs on the main thread, blocking until the server exits.</li>
 * </ol>
 * </p>
 * <p>
 * <b>Security & Compatibility:</b>
 * Due to the use of a bundled <b>Vanilla</b> Minecraft server (which lacks modern proxy forwarding support),
 * the wrapper currently configures the network in <b>Offline Mode</b>.
 * <ul>
 *   <li>Velocity {@code online-mode} is disabled.</li>
 *   <li>Backend {@code online-mode} is disabled.</li>
 *   <li>Velocity forwarding is set to {@code none}.</li>
 * </ul>
 * This ensures successful connections for both Java and Bedrock clients but disables official Mojang authentication.
 * </p>
 * <p>
 * <b>Lifecycle Management:</b>
 * The wrapper keeps running as long as the Vanilla Server is active. When the Vanilla Server
 * stops (e.g., via GUI or /stop command), the wrapper automatically shuts down the Velocity Proxy
 * and exits.
 * </p>
 */
public class App {

    private static final String SERVER_DIR_NAME = "minecraft_server";
    private static final String PROXY_DIR_NAME = "velocity_proxy";
    private static final String SERVER_JAR_NAME = "server.jar";
    private static final String EULA_FILE_NAME = "eula.txt";

    /**
     * Main entry point.
     *
     * @param args Command line arguments (unused).
     */
    public static void main(String[] args) {
        File serverDir = new File(SERVER_DIR_NAME);
        File proxyDir = new File(PROXY_DIR_NAME);
        
        try {
            System.out.println("=== Wrapper: Initialization ===");

            // --- Phase 1: Installation ---
            // 1. Vanilla Server
            ServerInstaller serverInstaller = new ServerInstaller(serverDir, SERVER_JAR_NAME, EULA_FILE_NAME);
            serverInstaller.install();

            // 2. Velocity Proxy & Plugins
            VelocityInstaller velocityInstaller = new VelocityInstaller(proxyDir);
            velocityInstaller.install();

            // --- Phase 2: Configuration ---
            System.out.println("=== Wrapper: Configuration ===");
            
            // Generate Security Token
            String secret = SecretManager.generateSecret();
            System.out.println("Wrapper: Generated Forwarding Secret: " + secret);

            // Configure Vanilla (Backend)
            File propertiesFile = new File(serverDir, "server.properties");
            ServerPropertiesManager serverConfig = new ServerPropertiesManager(propertiesFile);
            serverConfig.load();
            serverConfig.applyEnvironmentVariables(); 
            
            // ENFORCE Architecture Constraints
            System.out.println("Config: Enforcing Proxy Architecture settings...");
            serverConfig.setProperty("server-port", "25566");      // Private backend port
            serverConfig.setProperty("online-mode", "false");      // Delegate auth to Proxy
            serverConfig.save();
            
            // Configure Paper (Backend) - Velocity Forwarding
            PaperConfigManager paperConfig = new PaperConfigManager(serverDir);
            paperConfig.configure(secret);

            // Configure Proxy
            VelocityRunner velocityRunner = new VelocityRunner(proxyDir);
            velocityRunner.configure(secret);

            // --- Phase 3: Execution ---
            NetworkReporter.printReport();
            
            // A. Start Frontend (Velocity) - Background Process
            velocityRunner.start();

            // B. Start Backend (Vanilla) - Blocking the Main Thread
            // The Wrapper stays alive as long as the Vanilla Server is running.
            boolean enableGui = Boolean.parseBoolean(System.getenv().getOrDefault("MC_GUI", "true"));
            ServerRunner serverRunner = new ServerRunner(serverDir, SERVER_JAR_NAME);
            
            int exitCode = 0;
            try {
                exitCode = serverRunner.start(enableGui);
                System.out.println("Wrapper: Backend Server exited with code: " + exitCode);
            } catch (Exception e) {
                System.err.println("Wrapper: Backend Server crashed: " + e.getMessage());
                e.printStackTrace();
                exitCode = 1;
            } finally {
                // C. Cleanup: Ensure Proxy is shut down when Backend stops
                System.out.println("Wrapper: Shutting down Proxy...");
                velocityRunner.stop();
            }
            
            System.exit(exitCode);

        } catch (Exception e) {
            System.err.println("Wrapper Error [Critical]: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
