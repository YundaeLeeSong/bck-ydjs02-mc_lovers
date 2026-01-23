package minecraft.wrapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the installation of Minecraft plugins.
 * <p>
 * Ensures that all required plugins are present in the server's 'plugins' directory.
 * If any resources are missing from the classpath, it reports the issue and halts execution.
 * </p>
 */
public class PluginsLoader {

    private final File serverDir;
    
    // Map<FileName, PluginInfo>
    private static final Map<String, PluginInfo> PLUGINS = new LinkedHashMap<>();

    static {
        PLUGINS.put("Geyser-Spigot.jar", new PluginInfo(
            "Geyser for Spigot",
            "https://geysermc.org/download/?project=geyser"
        ));
        PLUGINS.put("floodgate-spigot.jar", new PluginInfo(
            "Floodgate for Spigot",
            "https://geysermc.org/download/?project=floodgate"
        ));
        PLUGINS.put("ViaVersion.jar", new PluginInfo(
            "ViaVersion",
            "https://hangar.papermc.io/ViaVersion/ViaVersion/versions"
        ));
        PLUGINS.put("ViaBackwards.jar", new PluginInfo(
            "ViaBackwards",
            "https://hangar.papermc.io/ViaVersion/ViaBackwards/versions"
        ));
    }

    /**
     * Constructs a new PluginsLoader.
     *
     * @param serverDir The server's root directory.
     */
    public PluginsLoader(File serverDir) {
        this.serverDir = serverDir;
    }

    /**
     * Installs all required plugins to the {@code plugins} directory.
     * <p>
     * Checks for missing resources in the classpath first. If any are missing,
     * it prints a detailed report with download links and throws a RuntimeException.
     * </p>
     *
     * @throws IOException If file operations fail.
     */
    public void install() throws IOException {
        File pluginsDir = new File(serverDir, "plugins");
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
        }

        System.out.println("PluginsLoader: Checking and installing plugins...");
        
        List<String> missingPlugins = new ArrayList<>();

        for (Map.Entry<String, PluginInfo> entry : PLUGINS.entrySet()) {
            String fileName = entry.getKey();
            
            // Check if resource exists
            if (App.class.getResource("/plugins/" + fileName) == null) {
                missingPlugins.add(fileName);
                continue;
            }

            installPlugin(pluginsDir, fileName);
        }

        if (!missingPlugins.isEmpty()) {
            printMissingResourcesReport(missingPlugins);
            throw new RuntimeException("Missing required plugin resources. See report above.");
        }
    }

    /**
     * Copies a single plugin from resources to disk.
     *
     * @param pluginsDir The target directory.
     * @param pluginName The filename of the plugin.
     * @throws IOException If the copy operation fails.
     */
    private void installPlugin(File pluginsDir, String pluginName) throws IOException {
        File targetFile = new File(pluginsDir, pluginName);
        String resourcePath = "/plugins/" + pluginName;

        try (InputStream is = App.class.getResourceAsStream(resourcePath)) {
            // Should not happen due to check above, but safe guard
            if (is == null) return; 
            Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("PluginsLoader: Installed " + pluginName);
        }
    }

    /**
     * Prints a report of missing plugin resources.
     *
     * @param missingFiles List of missing filenames.
     */
    private void printMissingResourcesReport(List<String> missingFiles) {
        System.err.println("\n=== Build Configuration Required ===");
        System.err.println("The wrapper is configured to bundle plugins from resources,");
        System.err.println("but they were not found in the classpath (src/main/resources/plugins/).");
        System.err.println("-------------------------------------------------------------------------------");

        for (String fileName : missingFiles) {
            PluginInfo info = PLUGINS.get(fileName);
            System.err.println("[MISSING] " + info.name);
            System.err.println("  > Download: " + info.url);
            System.err.println("  > Action:   Ensure name is '" + fileName + "' and place in 'app/src/main/resources/plugins/'");
            System.err.println("");
        }

        System.err.println("-------------------------------------------------------------------------------");
        System.err.println("After placing the files, rebuild the project.");
        System.err.println("=================================================\n");
    }

    /**
     * Data holder for plugin metadata.
     */
    private static class PluginInfo {
        String name;
        String url;

        PluginInfo(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}
