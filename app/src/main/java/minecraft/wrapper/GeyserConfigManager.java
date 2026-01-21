package minecraft.wrapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.ArrayList;

/**
 * <b>Service: Geyser Configuration Manager</b>
 * <p>
 * Manages the {@code plugins/Geyser-Velocity/config.yml} file.
 * Ensures the Geyser configuration is optimized for cloud environments (OCI),
 * specifically setting the MTU to prevent packet fragmentation and timeouts.
 * </p>
 */
public class GeyserConfigManager {

    private final File proxyDir;

    public GeyserConfigManager(File proxyDir) {
        this.proxyDir = proxyDir;
    }

    /**
     * Configures Geyser with necessary overrides.
     *
     * @throws IOException If file operations fail.
     */
    public void configure() throws IOException {
        File pluginsDir = new File(proxyDir, "plugins");
        File geyserDir = new File(pluginsDir, "Geyser-Velocity");
        if (!geyserDir.exists()) {
            geyserDir.mkdirs();
        }

        File configFile = new File(geyserDir, "config.yml");
        
        if (!configFile.exists()) {
            // Case 1: File doesn't exist (Fresh Install)
            String content = createDefaultConfig();
            Files.writeString(configFile.toPath(), content, StandardOpenOption.CREATE);
            System.out.println("Config: Created Geyser config.yml with OCI optimizations.");
        } else {
            // Case 2: File exists (Restart)
            updateExistingConfig(configFile);
        }
    }

    private String createDefaultConfig() {
        return """        
bedrock:
  address: 0.0.0.0
  port: 19132
  clone-remote-port: false

advanced:
  floodgate-key-file: \"floodgate-key.pem\"
  bedrock:
    # OCI/Cloud Fix: Lower MTU to prevent packet loss and timeouts
    mtu: 1200

java:
  auth-type: floodgate

saved-user-logins:
  - \"false\"
pending-authentication-timeout: 120
command-suggestions: true
passthrough-motd: true
passthrough-protocol-name: true
legacy-ping-passthrough: true
ping-passthrough-interval: 3

debug-mode: false
allow-third-party-capes: true
show-cooldown: \"title\"
emote-offhand-workaround: \"disabled\"
default-locale: \"en_us\"
cache-images: 0
allow-custom-skulls: true
max-visible-custom-skulls: 50
add-non-bedrock-items: true
above-bedrock-nether-building: false
force-resource-packs: true
xbox-achievements-enabled: false
log-player-ip-addresses: true
notify-on-new-bedrock-update: true
unusable-space-block: \"minecraft:barrier\"
""";
    }

    private void updateExistingConfig(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String newContent = content;
        
        // Fix MTU for OCI/Cloud
        // Matches "mtu: [number]" and replaces with "mtu: 1200"
        if (newContent.contains("mtu:")) {
             newContent = newContent.replaceAll("mtu: \\d+", "mtu: 1200");
        } else {
             // Fallback if key missing (unlikely in valid config), append to bedrock section is hard without yaml parser
             // We'll just append it to the end if not found, though Geyser might not read it if structure is strict.
             // Better to assume it exists in default config or we leave it alone if file is totally custom/broken.
             System.out.println("Config: Warning - 'mtu' key not found in Geyser config. Skipping update.");
        }
        
        if (!newContent.equals(content)) {
            Files.writeString(file.toPath(), newContent, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Config: Updated Geyser config.yml (MTU set to 1200).");
        }
    }
}
