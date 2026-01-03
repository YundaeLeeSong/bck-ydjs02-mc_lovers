package minecraft.wrapper;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * <b>Service: User Feedback</b>
 * <p>
 * Responsible for displaying the network topology and connection information to the console
 * when the server starts. This helps users understand how to connect (Java vs Bedrock).
 * </p>
 */
public class NetworkReporter {

    /**
     * Prints a formatted table summarizing the server's network configuration.
     * <p>
     * Displays:
     * <ul>
     *   <li>Connection ports (25565 TCP, 19132 UDP).</li>
     *   <li>Data flow (Proxy -> Backend).</li>
     *   <li>Authentication methods.</li>
     * </ul>
     * </p>
     */
    public static void printReport() {
        System.out.println("\n=== Network Configuration Report ===");
        try {
            /*
             * METHOD 1: InetAddress.getByName("localhost")
             * --------------------------------------------
             * This explicitly looks up the name "localhost".
             * This will almost ALWAYS return 127.0.0.1 (IPv4) or ::1 (IPv6).
             * It represents the "Loopback Interface".
             */
            InetAddress loopback = InetAddress.getByName("localhost");

            /*
             * METHOD 2: InetAddress.getLocalHost()
             * ------------------------------------
             * This retrieves the actual Hostname of the OS (e.g., "Production-Server-1")
             * and resolves it.
             *
             * If your machine is connected to a network, this usually returns the
             * IP address assigned to your Network Interface Card (NIC).
             */
            InetAddress localhost = InetAddress.getLocalHost();
            System.out.println("Localhost IP: " + localhost.getHostAddress());
            System.out.println("Localhost Hostname: " + localhost.getHostName());
            System.out.println("Localhost Canonical Hostname: " + localhost.getCanonicalHostName());
            System.out.println("Localhost Address: " + localhost.getAddress());
            System.out.println("Localhost Address Length: " + localhost.getAddress().length);
            System.out.println("Localhost Address String: " + localhost.getAddress().toString());
        } catch (UnknownHostException e) {
            System.out.println("Error getting IP address: " + e.getMessage());
        }
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("| Feature             | Java Edition (PC)        | Bedrock Edition (Mobile/Console) |");
        System.out.println("|---------------------|--------------------------|----------------------------------|");
        System.out.println("| Primary Port        | 25565 (TCP)              | 19132 (UDP)                      |");
        System.out.println("| Initial Target      | Velocity Proxy           | Geyser (via Velocity)            |");
        System.out.println("| Authentication      | Mojang (Native)          | Floodgate (No Java Account Req)  |");
        System.out.println("| Backend Server      | Vanilla (Internal)       | Vanilla (Internal)               |");
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("Backend is listening on 127.0.0.1:25566 (Protected)");
        System.out.println("Proxy is listening on 0.0.0.0:25565 (Public)");
        System.out.println("====================================\n");
    }
}
