package minecraft.wrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>Service: Process Lifecycle Manager</b>
 * <p>
 * This class handles the low-level details of executing the Minecraft Server process.
 * It is responsible for:
 * <ul>
 *   <li>Constructing the command line arguments.</li>
 *   <li>Locating the correct Java runtime (bundled or system).</li>
 *   <li>Launching the process.</li>
 *   <li><b>Monitoring and Terminating</b> the process gracefully.</li>
 * </ul>
 * </p>
 */
public class ServerRunner {

    private final File workingDir;
    private final String jarName;
    private Process serverProcess;

    /**
     * @param workingDir The directory where the server process should be rooted.
     * @param jarName    The filename of the server executable jar.
     */
    public ServerRunner(File workingDir, String jarName) {
        this.workingDir = workingDir;
        this.jarName = jarName;
    }

    /**
     * Configuration: Starts the server process.
     * <p>
     * <b>Blocking Operation:</b> This method blocks the calling thread until the server process exits.
     * It pipes the server's standard output and error streams to the wrapper's console.
     * </p>
     *
     * @param enableGui If {@code true}, the server's GUI window is allowed to open.
     *                  If {@code false}, the {@code nogui} argument is passed.
     * @return The process exit code (0 usually indicates success).
     * @throws IOException If the process cannot be started (e.g., java not found).
     * @throws InterruptedException If the wrapper thread is interrupted while waiting.
     */
    public int start(boolean enableGui) throws IOException, InterruptedException {
        // 1. Resolve Java Path
        // We prioritize 'java.home' to support bundled runtimes (jpackage).
        String javaHome = System.getProperty("java.home");
        String javaBin = System.getProperty("os.name").toLowerCase().contains("win") ? "bin/java.exe" : "bin/java";
        String javaPath = new File(javaHome, javaBin).getAbsolutePath();
        
        // 2. Build Command Arguments
        List<String> commands = new ArrayList<>();
        commands.add(javaPath);
        commands.add("-Xmx1024M"); // Limit Heap to 1GB
        commands.add("-Xms1024M"); // Start Heap at 1GB
        commands.add("-jar");
        commands.add(jarName);
        if (!enableGui) {
            commands.add("nogui"); // Headless mode
        }

        System.out.println("Runner: Launching Server...");
        System.out.println("Runner: Command -> " + String.join(" ", commands));
        
        // 3. Configure ProcessBuilder
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.directory(workingDir);
        pb.inheritIO(); // Pipe server output to wrapper console

        // 4. Start Process
        this.serverProcess = pb.start();

        // 5. Register Graceful Shutdown Hook
        // If the wrapper is killed (Ctrl+C), this hook ensures the server dies too.
        Thread shutdownHook = new Thread(this::shutdown, "Minecraft-Shutdown-Hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // 6. Block until server exits
        int exitCode = this.serverProcess.waitFor();
        
        // Cleanup hook if normal exit occurred
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // Ignored: Hook may already be running if we are shutting down.
        } 
        
        return exitCode;
    }

    /**
     * <b>Shutdown Logic:</b> Terminate the server process immediately.
     * <p>
     * This method attempts to kill the entire process tree to ensure no "zombie"
     * processes (like GUI windows) are left behind.
     * </p>
     * <p>
     * Strategy:
     * <ol>
     *   <li>Java 9+ {@code descendants().destroyForcibly()} (Cross-platform).</li>
     *   <li>Windows {@code taskkill /F /T} fallback (Nuclear option for stubborn GUIs).</li>
     * </ol>
     * </p>
     */
    private void shutdown() {
        if (this.serverProcess != null && this.serverProcess.isAlive()) {
            System.out.println("\nRunner: Shutdown Hook Triggered. Terminating process tree...");
            long pid = this.serverProcess.pid();

            // Strategy 1: Standard Java API
            try {
                this.serverProcess.toHandle().descendants().forEach(handle -> {
                    System.out.println("Runner: Killing descendant PID " + handle.pid());
                    handle.destroyForcibly();
                });
                this.serverProcess.destroyForcibly();
            } catch (Exception e) {
                System.err.println("Runner: Java kill failed: " + e.getMessage());
            }

            // Strategy 2: Windows Fallback
            // Java's ProcessHandle sometimes misses GUI child windows on Windows due to handle permissions.
            // We use the OS native tool to be sure.
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    System.out.println("Runner: Attempting Windows TaskKill for PID " + pid);
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
                        .inheritIO()
                        .start()
                        .waitFor();
                } catch (Exception e) {
                    System.err.println("Runner: Windows TaskKill failed: " + e.getMessage());
                }
            }

            System.out.println("Runner: Server process terminated.");
        }
    }
}
