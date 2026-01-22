package minecraft.wrapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * <b>Service: Velocity Configuration Manager</b>
 * <p>
 * Manages the {@code velocity.toml} file.
 * Ensures the Velocity Proxy is configured with high timeout values to tolerate
 * slow backend servers (like Cloud Free Tier instances) and correct forwarding settings.
 * </p>
 */
public class VelocityConfigManager {

    private final File workDir;

    public VelocityConfigManager(File workDir) {
        this.workDir = workDir;
    }

    /**
     * Configures Velocity with necessary overrides.
     *
     * @throws IOException If file operations fail.
     */
    public void configure() throws IOException {
        File configFile = new File(workDir, "velocity.toml");
        
        if (!configFile.exists()) {
            // Case 1: File doesn't exist (Fresh Install)
            String content = getVelocityConfig();
            Files.writeString(configFile.toPath(), content, StandardOpenOption.CREATE);
            System.out.println("Config: Generated velocity.toml with Cloud optimizations.");
        } else {
            // Case 2: File exists (Restart)
            updateExistingConfig(configFile);
        }
    }

    private String getVelocityConfig() {
        return """
config-version = \"2.7\"
bind = \"0.0.0.0:25565\"
motd = \"&3A Velocity Proxy\"
show-max-players = 500
online-mode = false
prevent-client-proxy-connections = false
# Modern forwarding is required for Paper servers with velocity.enabled = true
player-info-forwarding-mode = \"modern\"
forwarding-secret-file = \"forwarding.secret\"
announce-forge = false
kick-existing-players = false
force-key-authentication = false
ping-passthrough = \"ALL\"
# High timeouts for slow Cloud/OCI servers
read-timeout = 300000
connection-timeout = 60000

[servers]
lobby = \"127.0.0.1:25566\"
try = [\"lobby\"]

[forced-hosts]
""";
    }

    private void updateExistingConfig(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String newContent = content;
        
        boolean modified = false;

        // 1. Enforce Modern Forwarding
        if (newContent.contains("player-info-forwarding-mode")) {
            if (!newContent.contains("player-info-forwarding-mode = \"modern\"")) {
                newContent = newContent.replaceAll("player-info-forwarding-mode = \".*?\"", "player-info-forwarding-mode = \"modern\"");
                modified = true;
            }
        } else {
            newContent += "\nplayer-info-forwarding-mode = \"modern\"";
            modified = true;
        }

        // 2. Enforce High Read Timeout (5 minutes)
        // Matches "read-timeout = [digits]"
        if (newContent.contains("read-timeout")) {
             if (!newContent.contains("read-timeout = 300000")) {
                 newContent = newContent.replaceAll("read-timeout = \\d+", "read-timeout = 300000");
                 modified = true;
             }
        } else {
             newContent += "\nread-timeout = 300000";
             modified = true;
        }
        
        // 3. Enforce Connection Timeout (30s)
        if (newContent.contains("connection-timeout")) {
             if (!newContent.contains("connection-timeout = 60000")) {
                 newContent = newContent.replaceAll("connection-timeout = \\d+", "connection-timeout = 60000");
                 modified = true;
             }
        } else {
             newContent += "\nconnection-timeout = 60000";
             modified = true;
        }
        
        if (modified) {
            Files.writeString(file.toPath(), newContent, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Config: Updated velocity.toml with high timeouts (OCI Fix).");
        }
    }
}
