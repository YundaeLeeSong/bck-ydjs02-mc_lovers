# Minecraft Server Wrapper

A robust, self-contained wrapper application for managing a Purpur (Minecraft Java Edition) server with built-in Bedrock Edition support (via Geyser/Floodgate). This tool handles installation, configuration, and lifecycle management, ensuring a smooth experience for both developers and server administrators.

## Features

*   **Automatic Installation**: Automatically extracts `server.jar` and required plugins (`Geyser`, `Floodgate`, `ViaVersion`, `ViaBackwards`) and accepts the EULA.
*   **Shadow Configuration**: Performs an automated "Shadow Run" on the first launch to generate default configuration files before the user session begins.
*   **Environment-Based Configuration**: Configure server properties (`motd`, `max-players`, `online-mode`, etc.) using simple Environment Variables.
*   **Graceful Shutdown**: Robustly handles `Ctrl+C` signals to ensure the server process terminates cleanly, preventing "zombie" processes.
*   **Cross-Platform**: Built on standard Java 21, runnable on Windows, Linux, and macOS.
*   **Native Distribution**: Can be packaged into a standalone executable (using `jpackage`) that includes its own Java runtime.
*   **Cloud Optimized**: Automatically enforces `mtu: 1200` in Geyser configuration to prevent timeout issues on cloud networks (like OCI).

## Usage

### Configuration
You can configure the server by setting the following Environment Variables before running the wrapper.

| Variable         | Default | Description |
| ---------------- | ------- | ----------- |
| `MC_GUI`         | `true`  | If `true`, shows the Minecraft Server GUI window. If `false`, runs in headless (CLI) mode. |
| `MC_MOTD`        | *Default* | The "Message of the Day" shown in the server list. |
| `MC_MAX_PLAYERS` | `10`    | Maximum number of players allowed. |
| `MC_ONLINE_MODE` | `true`  | Verify player accounts with Mojang servers. |

**Example (PowerShell):**
```powershell
$env:MC_GUI="false"; $env:MC_MOTD="My Production Server"; .\dist\mc-lovers\mc-lovers.exe
```

### Running from Source (Developers)
Requires Java 21+ and Gradle.

```bash
# To run
./gradlew clean run

# To distribute
./gradlew clean jpackage
```

## Distribution

This project supports creating a native, standalone executable using `jpackage`. This eliminates the need for the end-user to have Java installed system-wide.

### Prerequisites
*   JDK 21 or higher installed.
*   Gradle.

### Build Steps

1.  **Build the Project**:
    Run the `jpackage` task.
    ```powershell
    .\gradlew.bat clean jpackage
    ```

2.  **Run the Distribution**:
    The executable will be located in `app/build/dist/mc-lovers/`.
    ```powershell
    .\app\build\dist\mc-lovers\mc-lovers.exe
    ```

## Project Structure

*   `app/src/main/java/minecraft/wrapper/`
    *   `App.java`: Main entry point and orchestrator.
    *   `ServerLoader.java`: Handles server jar setup.
    *   `PluginsLoader.java`: Installs required plugins (`Geyser`, `Floodgate`, etc.).
    *   `ServerConfig.java`: Manages `server.properties` and env vars.
    *   `GeyserConfig.java`: Optimizes Geyser configuration (MTU fix).
    *   `ServerRunner.java`: Manages the server process lifecycle and "Shadow Run" logic.
*   `app/src/main/resources/`: Contains the bundled `server.jar` and plugins.

## Architecture

This wrapper implements a **Single-Server Architecture**:
*   **Server**: Purpur (Fork of Paper/Spigot).
*   **Ports**:
    *   **25565 (TCP):** Java Edition.
    *   **19132 (UDP):** Bedrock Edition (via Geyser plugin).
*   **Compatibility**: Includes `ViaVersion` and `ViaBackwards` to allow clients from newer and older Minecraft versions to connect.

### "Shadow Run" Logic
On the first launch (or if configs are missing), the wrapper starts the server in a special headless mode. It waits for initialization to complete and then immediately shuts it down. This ensures all default configuration files (`server.properties`, `config.yml`) are generated on disk. The wrapper then modifies these files (e.g., setting `online-mode`, adjusting MTU) before starting the actual server session.

## Troubleshooting

**"File locked" error on startup:**
This usually means a previous instance of the server is still running in the background.
*   **Fix:** Ensure you exit the wrapper using `Ctrl+C` or following the prompt. Avoid using Task Manager to kill the wrapper unless necessary.

**Bedrock Connection Timeout:**
*   The wrapper automatically fixes this by enforcing `mtu: 1200` in the Geyser config, which is critical for Cloud environments (OCI, AWS).
