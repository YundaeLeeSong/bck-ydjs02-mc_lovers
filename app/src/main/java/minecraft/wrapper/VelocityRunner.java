package minecraft.wrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>Service: Velocity Proxy Manager</b>
 * <p>
 * Manages the lifecycle of the Velocity Proxy process.
 * This includes configuration generation, process startup, and graceful shutdown.
 * </p>
 * <p>
 * The proxy runs as a separate process (Frontend) that forwards traffic to the
 * Vanilla Server (Backend).
 * <br>
 * <b>Compatibility Note:</b>
 * To support Vanilla Minecraft backends (which lack native modern forwarding),
 * this runner currently defaults to {@code player-info-forwarding-mode = "none"}
 * and {@code online-mode = false}. This bypasses strict key validation but loses
 * IP forwarding capabilities.
 * </p>
 */
public class VelocityRunner {

    private final File workDir;
    private final File javaBin;
    private Process process;

    /**
     * Creates a new VelocityRunner.
     *
     * @param workDir The directory where Velocity will run.
     */
    public VelocityRunner(File workDir) {
        this.workDir = workDir;
        // Resolve Java Path (same as ServerRunner)
        String javaHome = System.getProperty("java.home");
        String bin = System.getProperty("os.name").toLowerCase().contains("win") ? "bin/java.exe" : "bin/java";
        this.javaBin = new File(javaHome, bin);
    }

    /**
     * Generates the {@code velocity.toml} configuration file and the {@code forwarding.secret} file.
     * <p>
     * The generated configuration is optimized for this wrapper's Vanilla backend:
     * <ul>
     *   <li>Binds to 0.0.0.0:25565 (Public Port).</li>
     *   <li>Forwards to 127.0.0.1:25566 (Internal Backend).</li>
     *   <li>Sets {@code online-mode = false} to bypass key validation on offline backends.</li>
     *   <li>Sets forwarding mode to {@code none} (Vanilla compatibility).</li>
     * </ul>
     * </p>
     *
     * @param secret The forwarding secret to write to {@code forwarding.secret} (unused in "none" mode but kept for future use).
     * @throws IOException If writing the configuration files fails.
     */
    public void configure(String secret) throws IOException {
        // 1. Write Secret File
        // We always overwrite this to ensure the Proxy and Backend are in sync with the Wrapper's state.
        File secretFile = new File(workDir, "forwarding.secret");
        try (FileWriter writer = new FileWriter(secretFile)) {
            writer.write(secret);
        }

        // 2. Write Config File
        File configFile = new File(workDir, "velocity.toml");
        if (!configFile.exists()) {
            System.out.println("VelocityRunner: Generating velocity.toml...");
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(getVelocityConfig());
            }
        }
    }

    /**
     * Starts the Velocity Proxy process in the background.
     * <p>
     * This method does not block. It spawns the process and returns immediately.
     * It also registers a JVM shutdown hook to ensure the proxy is killed if the wrapper terminates.
     * </p>
     *
     * @throws IOException If the process cannot be started.
     */
    public void start() throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin.getAbsolutePath());
        cmd.add("-Xmx512M"); // Velocity is lightweight
        cmd.add("-jar");
        cmd.add("velocity.jar");

        System.out.println("VelocityRunner: Starting Proxy on port 25565...");
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        pb.redirectOutput(Redirect.INHERIT);
        pb.redirectError(Redirect.INHERIT);
        
        this.process = pb.start();

        // Register Graceful Shutdown Hook
        Thread shutdownHook = new Thread(this::stop, "Velocity-Shutdown-Hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Stops the Velocity Proxy process if it is running.
     * <p>
     * Sends a termination signal (SIGTERM/SIGKILL) to the process.
     * </p>
     */
    public void stop() {
        if (this.process != null && this.process.isAlive()) {
            System.out.println("VelocityRunner: Stopping Proxy...");
            this.process.destroy(); 
            // Velocity usually shuts down fast. We won't block here to keep it simple,
            // or we could wait a bit.
        }
    }
    
    /**
     * Waits for the Velocity process to exit.
     *
     * @return The exit code of the process.
     * @throws InterruptedException If the thread is interrupted while waiting.
     */
    public int waitFor() throws InterruptedException {
        if (this.process != null) {
            return this.process.waitFor();
        }
        return 0;
    }

    private String getVelocityConfig() {
        return """
config-version = "2.7"
bind = "0.0.0.0:25565"
motd = "&3A Velocity Proxy"
show-max-players = 500
online-mode = false
prevent-client-proxy-connections = false
# legacy is required for Vanilla servers that don't support modern forwarding
player-info-forwarding-mode = "none"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false
force-key-authentication = false
ping-passthrough = "ALL"

[servers]
lobby = "127.0.0.1:25566"
try = ["lobby"]

[forced-hosts]
""";
    }
}
