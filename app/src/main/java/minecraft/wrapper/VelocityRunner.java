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
     * Generates the {@code forwarding.secret} file.
     * <p>
     * The main {@code velocity.toml} configuration is now managed by {@link VelocityConfigManager}
     * to ensure cloud optimizations are applied.
     * </p>
     *
     * @param secret The forwarding secret to write to {@code forwarding.secret}.
     * @throws IOException If writing the secret file fails.
     */
    public void configure(String secret) throws IOException {
        // 1. Write Secret File
        // We always overwrite this to ensure the Proxy and Backend are in sync with the Wrapper's state.
        File secretFile = new File(workDir, "forwarding.secret");
        try (FileWriter writer = new FileWriter(secretFile)) {
            writer.write(secret);
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
        List<String> commands = new ArrayList<>();
        commands.add(javaBin.getAbsolutePath());
        
        commands.add("-Xms256M");  // Start Heap at 256MB (for low memory systems)
        commands.add("-Xmx512M");  // Limit Heap to 512MB (for low memory systems)

        // cmd.add("-Xms64M");  // Minimum heap
        // cmd.add("-Xmx128M"); // Velocity is lightweight
        commands.add("-jar");
        commands.add("velocity.jar");

        System.out.println("VelocityRunner: Starting Proxy on port 25565...");
        
        ProcessBuilder pb = new ProcessBuilder(commands);
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
}
