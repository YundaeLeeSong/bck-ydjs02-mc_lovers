package minecraft.wrapper;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import static org.junit.Assert.*;

public class AppTest {
    
    @Test
    public void testGeyserConfigLogic() throws IOException {
        // TDD: Test the regex logic used in GeyserConfig without running the full app
        File tempDir = Files.createTempDirectory("geyser_test").toFile();
        tempDir.deleteOnExit();
        File pluginsDir = new File(tempDir, "plugins");
        File geyserDir = new File(pluginsDir, "Geyser-Spigot");
        geyserDir.mkdirs();
        File configFile = new File(geyserDir, "config.yml");
        
        // Scenario 1: Existing file with default MTU
        String initialContent = "bedrock:\n  mtu: 1400\nother: value";
        Files.writeString(configFile.toPath(), initialContent, StandardOpenOption.CREATE);
        
        GeyserConfig config = new GeyserConfig(tempDir);
        config.configure();
        
        String newContent = Files.readString(configFile.toPath());
        assertTrue("MTU should be updated to 1200", newContent.contains("mtu: 1200"));
        assertTrue("Other values should persist", newContent.contains("other: value"));
        
        // Cleanup
        configFile.delete();
        geyserDir.delete();
        pluginsDir.delete();
        tempDir.delete();
    }
}