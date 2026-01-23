package minecraft.wrapper;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * <b>Service: User Feedback</b>
 * <p>
 * Responsible for displaying the network topology and connection information to the console
 * when the server starts.
 * </p>
 */
public class NetworkReporter {

    /**
     * Prints a formatted table summarizing the server's network configuration.
     * <p>
     * Displays:
     * <ul>
     *   <li>Connection ports (25565 TCP, 19132 UDP).</li>
     *   <li>Backend server type (Purpur).</li>
     *   <li>Authentication methods.</li>
     * </ul>
     * </p>
     */
    public static void printReport() {
        System.out.println("\n=== Network Configuration Report ===");
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            System.out.println("Host: " + localhost.getHostName() + " (" + localhost.getHostAddress() + ")");
        } catch (UnknownHostException e) {
            System.out.println("Host: Unknown");
        }
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("| Feature             | Java Edition (PC)        | Bedrock Edition (Mobile/Console) |");
        System.out.println("|---------------------|--------------------------|----------------------------------|");
        System.out.println("| Primary Port        | 25565 (TCP)              | 19132 (UDP)                      |");
        System.out.println("| Server              | Purpur (Java)            | Geyser (Plugin)                  |");
        System.out.println("| Authentication      | Mojang (Native)          | Floodgate (No Java Account Req)  |");
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("Server is listening on 0.0.0.0:25565 (Public)");
        System.out.println("Geyser is listening on 0.0.0.0:19132 (Public UDP)");
        System.out.println("====================================\n");
    }
}
