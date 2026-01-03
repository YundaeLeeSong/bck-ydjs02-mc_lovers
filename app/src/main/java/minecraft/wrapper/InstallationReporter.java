package minecraft.wrapper;

/**
 * <b>Service: Installation Guidance</b>
 * <p>
 * Provides user-friendly feedback when manual installation steps are required.
 * Since the wrapper expects Velocity components to be bundled in the resources, 
 * this reporter guides the user on where to place them during development/build.
 * </p>
 */
public class InstallationReporter {

    /**
     * Prints a formatted report of missing files and where to put them.
     *
     * @param missingVelocity  True if velocity.jar is missing from resources.
     * @param missingGeyser    True if Geyser-Velocity.jar is missing from resources.
     * @param missingFloodgate True if floodgate-velocity.jar is missing from resources.
     */
    public static void printMissingResourcesReport(boolean missingVelocity, boolean missingGeyser, boolean missingFloodgate) {
        System.err.println("\n=== Build Configuration Required ===");
        System.err.println("The wrapper is configured to bundle Velocity and its plugins from resources,");
        System.err.println("but they were not found in the classpath (src/main/resources).");
        System.err.println("-------------------------------------------------------------------------------");
        
        if (missingVelocity) {
            System.err.println("[MISSING] Velocity Proxy Server");
            System.err.println("  > Download: https://papermc.io/downloads/velocity");
            System.err.println("  > Action:   Rename to 'velocity.jar' and place in 'app/src/main/resources/'");
            System.err.println("");
        }

        if (missingGeyser) {
            System.err.println("[MISSING] Geyser for Velocity");
            System.err.println("  > Download: https://geysermc.org/download");
            System.err.println("  > Action:   Ensure name is 'Geyser-Velocity.jar' and place in 'app/src/main/resources/plugins/'");
            System.err.println("");
        }

        if (missingFloodgate) {
            System.err.println("[MISSING] Floodgate for Velocity");
            System.err.println("  > Download: https://geysermc.org/download#floodgate");
            System.err.println("  > Action:   Ensure name is 'floodgate-velocity.jar' and place in 'app/src/main/resources/plugins/'");
            System.err.println("");
        }

        System.err.println("-------------------------------------------------------------------------------");
        System.err.println("After placing the files, rebuild the project.");
        System.err.println("=================================================\n");
    }
}