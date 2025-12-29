package minecraft.wrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * <b>Service: Configuration Manager</b>
 * <p>
 * Handles reading, modifying, and saving the {@code server.properties} file.
 * This class serves as the bridge between Environment Variables (configuration injection)
 * and the actual Minecraft Server configuration file.
 * </p>
 */
public class ServerPropertiesManager {
    private final File file;
    private final Properties properties;

    /**
     * @param file The {@code server.properties} file object.
     */
    public ServerPropertiesManager(File file) {
        this.file = file;
        this.properties = new Properties();
    }

    /**
     * Loads existing properties from disk.
     * Use this before applying changes to preserve existing user settings.
     */
    public void load() {
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                System.err.println("Config: Failed to load server.properties: " + e.getMessage());
            }
        }
    }

    /**
     * Injects configuration from System Environment Variables.
     * <p>
     * This allows server admins to configure the server (e.g., in Docker or scripts)
     * without manually editing text files.
     * </p>
     * <p>
     * <b>Supported Variables:</b>
     * <ul>
     *   <li>{@code MC_MOTD} -> {@code motd}: The message of the day.</li>
     *   <li>{@code MC_MAX_PLAYERS} -> {@code max-players}: Maximum player count.</li>
     *   <li>{@code MC_ONLINE_MODE} -> {@code online-mode}: Authentication mode.
     *       <br><i>Note: Must be 'false' to allow Velocity to handle auth.</i></li>
     *   <li>{@code MC_ENFORCE_SECURE_PROFILE} -> {@code enforce-secure-profile}:
     *       <br><i>Note: Forced to 'false' to prevent proxy connection issues.</i></li>
     * </ul>
     * </p>
     */
    public void applyEnvironmentVariables() {
        // Defaults are provided if the env vars are unset
        String motd = System.getenv().getOrDefault("MC_MOTD", "A Minecraft Server");
        String maxPlayers = System.getenv().getOrDefault("MC_MAX_PLAYERS", "10");
        String onlineMode = System.getenv().getOrDefault("MC_ONLINE_MODE", "false");
        // server.properties, you must set enforce-secure-profile=false, otherwise, Bedrock clients will be kicked.
        // We FORCE this to false by default for this wrapper unless explicitly overridden to true (which is not recommended).
        String enforceSecureProfile = System.getenv().getOrDefault("MC_ENFORCE_SECURE_PROFILE", "false");

        System.out.println("Config: Applying Environment Variables:");
        System.out.println("  - motd: " + motd);
        System.out.println("  - max-players: " + maxPlayers);
        System.out.println("  - online-mode: " + onlineMode + "\t ('false' for delegate auth to ProxiedBedrock)");
        System.out.println("  - enforce-secure-profile: " + enforceSecureProfile + "\t ('false' for Bedrock clients)");

        setProperty("motd", motd);
        setProperty("max-players", maxPlayers);
        setProperty("online-mode", onlineMode);
        setProperty("enforce-secure-profile", "false"); // Always enforce false for proxy compatibility
    }

    /**
     * Persists the current state of properties to disk.
     */
    public void save() {
        try (FileOutputStream out = new FileOutputStream(file)) {
            properties.store(out, "Minecraft server properties");
        } catch (IOException e) {
            System.err.println("Config: Failed to save server.properties: " + e.getMessage());
        }
    }

    /**
     * Sets a property value in memory. Does not save to disk until {@link #save()} is called.
     *
     * @param key   Property key (e.g., "motd").
     * @param value Property value.
     */
    public void setProperty(String key, Object value) {
        properties.setProperty(key, String.valueOf(value));
    }

    /**
     * Retrieves a property value.
     *
     * @param key          Property key.
     * @param defaultValue Value to return if key is missing.
     * @return The property value.
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
