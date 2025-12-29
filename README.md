# Minecraft Server Wrapper

A robust, self-contained wrapper application for managing a Minecraft Server. This tool handles installation, configuration, and lifecycle management, ensuring a smooth experience for both developers and server administrators.

## Features

*   **Automatic Installation**: Automatically extracts the `server.jar` and accepts the EULA (`eula.txt`) if missing.
*   **Environment-Based Configuration**: Configure server properties (`motd`, `max-players`, etc.) using simple Environment Variables.
*   **Graceful Shutdown**: Robustly handles `Ctrl+C` signals to ensure the Minecraft Server process (and any GUI windows) are terminated cleanly, preventing "zombie" processes and file locks.
*   **Cross-Platform**: Built on standard Java 21, runnable on Windows, Linux, and macOS.
*   **Native Distribution**: Can be packaged into a standalone executable (using `jpackage`) that includes its own Java runtime.

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
$env:MC_GUI="false"; $env:MC_MOTD="My Production Server"; .\dist\MinecraftWrapper\MinecraftWrapper.exe
```

### Running from Source (Developers)
Requires Java 21+ and Gradle.

```bash

# To run
gradle clean && gradle build && gradle run

# To distribute
gradle clean && gradle build && gradle createDist

```

## Distribution

This project supports creating a native, standalone executable using `jpackage`. This eliminates the need for the end-user to have Java installed system-wide.

### Prerequisites
*   JDK 21 or higher installed.
*   Gradle.

### Build Steps

1.  **Build the Project**:
    Assemble the application and dependencies.
    ```powershell
    .\gradlew.bat clean :app:installDist
    ```

2.  **Package the Application**:
    Run `jpackage` to create the distribution image.
    
    **Windows Example:**
    ```powershell
    jpackage --type app-image ^
      --input app/build/install/app/lib ^
      --dest dist ^
      --name MinecraftWrapper ^
      --main-jar app.jar ^
      --main-class minecraft.wrapper.App ^
      --win-console
    ```

3.  **Run the Distribution**:
    The executable will be located in `dist/MinecraftWrapper/`.
    ```powershell
    .\dist\MinecraftWrapper\MinecraftWrapper.exe
    ```

## Project Structure

*   `app/src/main/java/minecraft/wrapper/`
    *   `App.java`: Main entry point and orchestrator (Facade pattern).
    *   `ServerInstaller.java`: Handles file setup (extraction, EULA).
    *   `ServerPropertiesManager.java`: Manages `server.properties` and env vars.
    *   `ServerRunner.java`: Manages the server process lifecycle and shutdown hooks.
*   `app/src/main/resources/`: Contains the bundled `server.jar`.

## Troubleshooting

**"File locked" error on startup:**
This usually means a previous instance of the server is still running in the background.
*   **Fix:** The wrapper now includes a robust Shutdown Hook. Ensure you exit the wrapper using `Ctrl+C` or by closing the console window. Avoid using Task Manager to kill the wrapper unless necessary.
*   **Windows:** If the issue persists, the wrapper will attempt to use `taskkill` to force-close the process tree on the next shutdown event.
