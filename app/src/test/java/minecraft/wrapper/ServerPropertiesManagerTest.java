package minecraft.wrapper;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ServerPropertiesManagerTest {
    @Test
    public void testPropertiesManagement() throws IOException {
        // Create a temporary file for testing
        File tempFile = File.createTempFile("server", ".properties");
        tempFile.deleteOnExit();

        ServerPropertiesManager manager = new ServerPropertiesManager(tempFile);

        // Test setting properties
        manager.setProperty("motd", "Test Server");
        manager.setProperty("max-players", 20);
        manager.save();

        // Verify file content
        String content = Files.readString(tempFile.toPath());
        assertTrue(content.contains("motd=Test Server"));
        assertTrue(content.contains("max-players=20"));

        // Test loading properties
        ServerPropertiesManager newManager = new ServerPropertiesManager(tempFile);
        newManager.load();
        assertEquals("Test Server", newManager.getProperty("motd", "default"));
        assertEquals("20", newManager.getProperty("max-players", "0"));
    }
}
