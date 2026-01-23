package minecraft.wrapper;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ServerConfigTest {
    @Test
    public void testPropertiesManagement() throws IOException {
        // Create a temporary file for testing
        File tempFile = File.createTempFile("server", ".properties");
        tempFile.deleteOnExit();

        ServerConfig manager = new ServerConfig(tempFile);

        // Test setting properties
        manager.setProperty("motd", "Test Server");
        manager.setProperty("max-players", 20);
        manager.save();

        // Verify file content
        String content = Files.readString(tempFile.toPath());
        assertTrue("Content should contain set property", content.contains("motd=Test Server"));
        assertTrue("Content should contain set property", content.contains("max-players=20"));

        // Test loading properties
        ServerConfig newManager = new ServerConfig(tempFile);
        newManager.load();
        assertEquals("Test Server", newManager.getProperty("motd", "default"));
        assertEquals("20", newManager.getProperty("max-players", "0"));
    }
}
