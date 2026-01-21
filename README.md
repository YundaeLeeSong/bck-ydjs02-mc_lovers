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
./gradlew clean build run

# To distribute
./gradlew clean build jpackage

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

## Implementation Details (v2.0)

This wrapper implements a **Dual-Process Architecture** to provide cross-platform compatibility (Java & Bedrock) using a Vanilla backend.

### Architecture
1.  **Frontend (Velocity Proxy)**
    *   **Port 25565 (TCP):** Java Edition entry point.
    *   **Port 19132 (UDP):** Bedrock Edition entry point (via Geyser/Floodgate plugins).
    *   Runs as a background process managed by the wrapper.

2.  **Backend (Vanilla Server)**
    *   **Port 25566 (TCP):** Internal server port (protected).
    *   Runs as the primary blocking process.

### Offline / Compatibility Mode
To support the bundled **Vanilla** `server.jar` (which lacks native proxy forwarding support like Paper/Spigot), the wrapper runs in a special compatibility mode:
*   **Velocity Forwarding:** `none` (Disables modern forwarding handshake).
*   **Authentication:** `Offline Mode` (Disabled on both Proxy and Server).
*   **Chat Signing:** Enforced insecure to prevent 1.19+ validation errors.

**Trade-offs:**
*   **Pros:** Works out-of-the-box with the provided Vanilla jar. Supports Bedrock and Java simultaneously.
*   **Cons:** Player IPs appear as `127.0.0.1` in server logs. Official Mojang authentication is disabled (insecure).

### Offline Installation
The wrapper is fully self-contained. It bundles:
*   `server.jar`
*   `velocity.jar`
*   `Geyser-Velocity.jar`
*   `floodgate-velocity.jar`

These are extracted from the classpath to the working directory on the first run, ensuring no internet connection is required for installation.

## Recent Changes

### JPackage Task Update
*   **Renamed Task:** The Gradle task for creating the distribution has been renamed from `createDist` to `jpackage` to better reflect its purpose.
*   **Cross-Platform Fix:** The `--win-console` flag is now conditionally applied only when running on Windows. This resolves build errors on Linux environments (e.g., Docker containers) where this flag is invalid.
*   **Usage:** Run `./gradlew jpackage` to build the native distribution.

### Build System & CI Refactoring (v2.1)
*   **Unified Build Logic:** Integrated `jpackage` configuration directly into `app/build.gradle.kts`, removing reliance on external scripts and the `shadowJar` plugin. The build now uses the standard `installDist` output to prevent resource corruption of bundled jars.
*   **Runtime Patching:** The `jpackage` task now automatically patches the generated runtime image by copying the full `java` binary. This fixes issues where child processes (like the Minecraft Server) failed to spawn because the default stripped runtime lacked the necessary executable.
*   **CI/CD Pipeline:** Added a comprehensive GitHub Actions workflow (`release.yml`) that automates releases for:
    *   **Windows** (`windows-latest`)
    *   **Ubuntu** (`ubuntu-latest`)
    *   **Oracle Linux 9** (Containerized build ensuring Enterprise Linux compatibility).
*   **Oracle Linux Fix:** Addressed specific JMODs requirements for `jlink` on EL9 systems by ensuring `java-21-openjdk-jmods` is installed during the build process to fix `Module java.rmi not found` errors.
*   **Linux Runtime Layout Fix:** Corrected the post-processing logic to respect the different runtime directory structures between Windows (`runtime/`) and Linux (`lib/runtime/`). Additionally ensures the patched `java` binary has executable permissions (`chmod +x`) on non-Windows systems, resolving `No such file or directory` errors during startup.

## Why Paper?

We have transitioned from the standard Vanilla Mojang server to **Paper** for the backend. Here is why:

1.  **Performance**: Paper includes significant optimizations to the Minecraft game loop, chunk loading, and redstone mechanics, resulting in smoother gameplay and lower resource usage compared to Vanilla.
2.  **Velocity Support**: Paper has native support for **Velocity**'s modern forwarding. This allows the proxy to securely pass player information (IP, UUID, skin) to the backend server without relying on the insecure 'Offline Mode' hack required for Vanilla.
3.  **Fixes & Stability**: Paper patches many known exploits and bugs that exist in the Vanilla server software.
4.  **Configurability**: Paper offers extensive configuration options ('paper-global.yml') to fine-tune server behavior to your specific needs.

## Known Issues & Cloud Optimizations (v2.2)

We have implemented specific fixes to ensure stability on Cloud environments (like Oracle Cloud Infrastructure - OCI) and resource-constrained servers.

### 1. Bedrock Connection Timeout (OCI Fix)
*   **Issue:** Bedrock players would get disconnected with a 'Timed Out' error exactly 60 seconds after joining.
*   **Cause:** Cloud networks often have a lower **MTU** (Maximum Transmission Unit) than the standard 1500 bytes. Large UDP packets from Geyser (like initial chunk data) were being fragmented or dropped by the cloud provider's network layer.
*   **Fix:** The wrapper now automatically enforces a safe **MTU of 1350** in the Geyser configuration (`plugins/Geyser-Velocity/config.yml`). This ensures packets are small enough to pass through restrictive cloud networks without fragmentation.

### 2. 'Server Not Responding' Crash
*   **Issue:** The server would crash during startup or heavy lag spikes with the error: *"The server has not responded for 60 seconds! Creating thread dump"*.
*   **Cause:** The Vanilla/Paper **Watchdog** detects if the main thread is frozen. On slow machines (e.g., free tier cloud instances), generating the initial world spawn can take minutes, triggering a false-positive crash.
*   **Fix:** We have disabled the internal watchdog by setting `max-tick-time=-1` in `server.properties`. This allows the server indefinitely to load chunks or recover from lag spikes without killing itself.

### 3. 'Velocity not routing' Error
*   **Issue:** Players could not connect to the backend server, receiving a *"Velocity not routing to a valid backend server definition"* error.
*   **Cause:** A configuration mismatch. Paper servers require **Modern Forwarding** (with a secure secret) to be enabled on the proxy to accept connections securely.
*   **Fix:** The wrapper now correctly configures `velocity.toml` to use `player-info-forwarding-mode = "modern"` and shares a generated secret with the Paper backend.