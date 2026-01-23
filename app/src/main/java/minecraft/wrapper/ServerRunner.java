package minecraft.wrapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Manages the lifecycle of the Minecraft Server process.
 * <p>
 * Handles starting the server in both standard and "shadow" modes,
 * monitoring its output, and ensuring graceful shutdown via Singleton pattern.
 * </p>
 */
public class ServerRunner {

    private static volatile ServerRunner INSTANCE;
    private final File workingDir;
    private final String jarName;
    private volatile Process serverProcess;
    private final Object lock = new Object();

    /**
     * Private constructor for Singleton.
     *
     * @param workingDir The directory where the server will run.
     * @param jarName    The name of the server JAR file.
     */
    private ServerRunner(File workingDir, String jarName) {
        this.workingDir = workingDir;
        this.jarName = jarName;
    }

    /**
     * Returns the singleton instance of ServerRunner.
     *
     * @param workingDir The directory where the server will run.
     * @param jarName    The name of the server JAR file.
     * @return The singleton instance.
     */
    public static ServerRunner getInstance(File workingDir, String jarName) {
        if (INSTANCE == null) {
            synchronized (ServerRunner.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ServerRunner(workingDir, jarName);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Executes a "Shadow Run" to generate default configuration files.
     * <p>
     * Starts the server in a headless mode, waits for initialization to complete
     * (detected via the "Done" log message), and then immediately sends the
     * "stop" command. This forces the server to write its default configs to disk.
     * </p>
     *
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted while waiting.
     */
    public void generateConfigs() throws IOException, InterruptedException {
        synchronized (lock) {
            System.out.println("Runner: Starting Shadow Run to generate configurations...");
            
            List<String> commands = buildJavaCommand(false); // Force nogui for shadow run
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.directory(workingDir);
            pb.redirectErrorStream(true); // Merge stderr into stdout
            
            Process process = pb.start();
            this.serverProcess = process;
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                
                String line;
                boolean initialized = false;
                
                // Monitor output for startup completion
                while ((line = reader.readLine()) != null) {
                    // Print shadow logs with a prefix
                    System.out.println("[Shadow] " + line);
                    
                    // "Done (s)!" is the standard Minecraft completion message
                    if (line.contains("Done (") && line.contains(")!")) {
                        System.out.println("Runner: Initialization complete. Stopping server...");
                        initialized = true;
                        
                        // Send 'stop' command to flush configs and save
                        writer.write("stop");
                        writer.newLine();
                        writer.flush();
                        break;
                    }
                }
                
                // If process exited without "Done", something went wrong
                if (!initialized && !process.isAlive()) {
                    System.err.println("Runner: Shadow run exited prematurely.");
                }
            }
            
            // Wait for the process to actually exit
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                System.err.println("Runner: Shadow run timed out during shutdown. Forcing kill.");
                process.destroyForcibly();
            } else {
                System.out.println("Runner: Shadow Run completed successfully.");
            }
            
            // Explicitly ensure cleanup using ProcessHandle just in case
            cleanupProcess(process);
            this.serverProcess = null;
            System.out.println("Runner: Shadow Run cleanup complete.");
        }
    }

    /**
     * Starts the server process and blocks until it exits.
     *
     * @param enableGui Whether to show the server GUI window.
     * @return The exit code of the server process.
     * @throws IOException          If the process cannot be started.
     * @throws InterruptedException If the wait is interrupted.
     */
    public int start(boolean enableGui) throws IOException, InterruptedException {
        synchronized (lock) {
            List<String> commands = buildJavaCommand(enableGui);

            System.out.println("Runner: Launching Server...");
            System.out.println("Runner: Command -> " + String.join(" ", commands));
            
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.directory(workingDir);
            pb.inheritIO(); 

            this.serverProcess = pb.start();
        }
        
        // Note: Shutdown hook is now managed by App.java
        return this.serverProcess.waitFor();
    }

    /**
     * Forcibly terminates the server process if it is running.
     * This method is intended to be called by the main application's shutdown handler.
     */
    public void stop() {
        synchronized (lock) {
            cleanupProcess(this.serverProcess);
        }
    }
    
    /**
     * Cleans up the process and its descendants using ProcessHandle.
     * 
     * @param process The process to clean up.
     */
    private void cleanupProcess(Process process) {
        if (process != null && process.isAlive()) {
            System.out.println("\nRunner: Terminating server process tree...");
            ProcessHandle handle = process.toHandle();
            
            // Terminate descendants first
            handle.descendants().forEach(child -> {
                try {
                    child.destroy();
                } catch (Exception e) {
                     // Ignore
                }
            });

            // Terminate main process
            handle.destroy();

            try {
                // Wait for graceful exit
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    System.out.println("Runner: Server unresponsive. Forcing tree shutdown.");
                     handle.descendants().forEach(ProcessHandle::destroyForcibly);
                     handle.destroyForcibly();
                }
            } catch (InterruptedException e) {
                System.out.println("Runner: Interrupted while waiting. Forcing tree shutdown.");
                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                handle.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Builds the command list for the Java process.
     *
     * @param enableGui Whether to enable the GUI.
     * @return The list of command arguments.
     */
    private List<String> buildJavaCommand(boolean enableGui) {
        String javaHome = System.getProperty("java.home");
        String javaBin = System.getProperty("os.name").toLowerCase().contains("win") ? "bin/java.exe" : "bin/java";
        String javaPath = new File(javaHome, javaBin).getAbsolutePath();
        
        List<String> commands = new ArrayList<>();
        commands.add(javaPath);
        commands.add("-Xms4096M");
        commands.add("-Xmx4096M"); // Reduced to 1024M
        commands.add("-jar");
        commands.add(jarName);
        if (!enableGui) {
            commands.add("nogui");
        }
        return commands;
    }
}
