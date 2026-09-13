# Requirements Document

## Introduction

The Minecraft server wrapper (`mc-lovers`) currently resolves its runtime root as a current-working-directory (CWD) relative path (`new File("minecraft_server")`). This couples the location of the extracted server jar, EULA, plugins, configs, and the world to wherever the process happens to be launched, which breaks the wrapper's platform-independence promise and produces inconsistent behavior across launch contexts.

This feature refactors the wrapper to resolve a stable, per-OS base runtime directory derived from the user home directory (`<user.home>/mclovers`), and to perform all file provisioning, configuration read/modify/save, and child-server-process execution under that base directory using cross-platform Java NIO file I/O (`java.nio.file`: `Path`, `Paths`, `Files`). All existing behaviors are preserved. The refactor keeps the "run one file, get a working cross-play server on a small VM" experience intact and correct on Windows, macOS, and Linux (including the Docker/Temurin Java 21 Linux target).

## Glossary

- **Wrapper**: The `mc-lovers` application (entry point `minecraft.wrapper.App`) that provisions and launches the Minecraft server as a child process.
- **Base_Runtime_Directory**: The stable per-OS root directory for all runtime artifacts, resolved as `<user.home>/mclovers`. Referred to below as the base directory.
- **User_Home**: The value of the Java system property `user.home`.
- **Directory_Resolver**: The Wrapper component responsible for computing and validating the Base_Runtime_Directory.
- **Server_Loader**: The component that provisions the server environment (directory creation, server jar extraction, EULA acceptance).
- **Plugins_Loader**: The component that installs bundled plugins into the `plugins` subdirectory of the Base_Runtime_Directory.
- **Server_Runner**: The component that launches and manages the Minecraft server child JVM process.
- **Server_Config**: The component that reads, modifies, and saves `server.properties`.
- **Geyser_Config**: The component that edits the existing Geyser `config.yml` in place.
- **Network_Reporter**: The component that prints the startup network report.
- **Shadow_Run**: A throwaway server start that generates default configuration files, then stops.
- **Child_Server_Process**: The separate Minecraft server JVM process spawned by the Server_Runner.
- **NIO_File_IO**: The `java.nio.file` API (`Path`, `Paths`, `Files`).
- **CWD**: The current working directory of the Wrapper process.

## Requirements

### Requirement 1: Resolve a stable per-OS base runtime directory

**User Story:** As an operator, I want the wrapper to always run under a stable per-user location, so that the server behaves identically regardless of where I launch the executable.

#### Acceptance Criteria

1. THE Directory_Resolver SHALL resolve the Base_Runtime_Directory as the `mclovers` child of the User_Home value returned by `System.getProperty("user.home")`.
2. WHERE the operating system is Windows, THE Directory_Resolver SHALL resolve the Base_Runtime_Directory to the `mclovers` directory under the value of the `%USERPROFILE%` home (as reflected by `user.home`).
3. WHERE the operating system is Linux or macOS, THE Directory_Resolver SHALL resolve the Base_Runtime_Directory to the `mclovers` directory under the user home directory (`~/mclovers`).
4. THE Directory_Resolver SHALL construct the Base_Runtime_Directory path using NIO_File_IO rather than a CWD-relative path.
5. THE Directory_Resolver SHALL resolve the Base_Runtime_Directory to an absolute path such that the resolved path is identical when the Wrapper is launched from any CWD.
6. WHEN the Directory_Resolver resolves the Base_Runtime_Directory and that directory does not exist, THE Directory_Resolver SHALL create the Base_Runtime_Directory, including any missing parent directories, using NIO_File_IO.
7. IF the User_Home value returned by `System.getProperty("user.home")` is null or an empty string, THEN THE Directory_Resolver SHALL abort resolution without creating any directory and SHALL produce an error indication reporting that User_Home is unavailable.
8. IF creation of the Base_Runtime_Directory fails, THEN THE Directory_Resolver SHALL abort startup and SHALL produce an error indication reporting that the Base_Runtime_Directory could not be created, leaving no partial Base_Runtime_Directory state relied upon by later phases.

### Requirement 2: Create and reuse the base directory idempotently

**User Story:** As an operator, I want the base directory created automatically on first run and reused afterward, so that repeated launches do not lose state or fail.

#### Acceptance Criteria

1. IF the Base_Runtime_Directory does not exist, THEN THE Directory_Resolver SHALL create the Base_Runtime_Directory and any missing parent directories using NIO_File_IO.
2. IF the Base_Runtime_Directory already exists as a directory, THEN THE Directory_Resolver SHALL reuse it without deleting, recreating, or modifying its existing contents.
3. WHEN a required subdirectory under the Base_Runtime_Directory is absent, THE Wrapper SHALL create that subdirectory using NIO_File_IO.
4. IF the path designated for the Base_Runtime_Directory already exists as a non-directory file, THEN THE Directory_Resolver SHALL halt startup and produce an error indication reporting the path conflict, without deleting or overwriting the existing file.
5. IF creation of the Base_Runtime_Directory or a required subdirectory fails, THEN THE Directory_Resolver SHALL halt startup and produce an error indication describing the failed path and cause, leaving any already-created directories intact.

### Requirement 3: Provision all artifacts under the base directory

**User Story:** As an operator, I want the server jar, EULA, and plugins provisioned under the base directory, so that all runtime artifacts live in one predictable location.

#### Acceptance Criteria

1. WHEN the server jar is absent from the Base_Runtime_Directory, THE Server_Loader SHALL extract the bundled server jar from the classpath into the Base_Runtime_Directory using NIO_File_IO.
2. IF the bundled server jar resource is missing from the classpath when extraction is required, THEN THE Server_Loader SHALL halt startup and produce an error indication reporting the missing resource.
3. IF the server jar already exists in the Base_Runtime_Directory, THEN THE Server_Loader SHALL preserve the existing server jar without overwriting it.
4. WHEN the EULA file is absent from the Base_Runtime_Directory, THE Server_Loader SHALL create the EULA file under the Base_Runtime_Directory with the accepted agreement value `eula=true`.
5. IF the EULA file already exists in the Base_Runtime_Directory, THEN THE Server_Loader SHALL preserve the existing EULA file without overwriting it.
6. WHEN the `plugins` subdirectory of the Base_Runtime_Directory is absent, THE Plugins_Loader SHALL create it using NIO_File_IO.
7. IF creation of the `plugins` subdirectory fails, THEN THE Plugins_Loader SHALL halt startup and produce an error indication reporting the failed path.
8. THE Plugins_Loader SHALL install each of the four bundled plugins (Geyser, Floodgate, ViaVersion, ViaBackwards) into the `plugins` subdirectory of the Base_Runtime_Directory using NIO_File_IO, overwriting any existing plugin file of the same name.
9. IF one or more bundled plugin resources are missing from the classpath, THEN THE Plugins_Loader SHALL install no plugins, print a report containing each missing plugin name and its official download link, and halt startup.
10. THE Server_Loader AND THE Plugins_Loader SHALL resolve all target paths relative to the Base_Runtime_Directory rather than relative to the CWD.

### Requirement 4: Read and write all configuration under the base directory

**User Story:** As an operator, I want all configuration files read and written under the base directory, so that my settings are applied to the same files the server uses.

#### Acceptance Criteria

1. THE Server_Config SHALL resolve the `server.properties` file under the Base_Runtime_Directory.
2. THE Geyser_Config SHALL resolve the Geyser `config.yml` file under the `plugins/Geyser-Spigot` path within the Base_Runtime_Directory.
3. THE Server_Config SHALL load, modify, and save `server.properties` under the Base_Runtime_Directory.
4. WHERE an expected Geyser configuration key is present, THE Geyser_Config SHALL edit that key in the existing Geyser `config.yml` in place under the Base_Runtime_Directory.
5. IF an expected Geyser configuration key is absent, THEN THE Geyser_Config SHALL skip editing that key and emit a warning rather than creating or corrupting the file.

### Requirement 5: Launch the child server process under the base directory

**User Story:** As an operator, I want the spawned server process to run in the base directory, so that all server-generated files (world, logs, properties) land in the same predictable location.

#### Acceptance Criteria

1. WHEN the Server_Runner starts the Child_Server_Process, THE Server_Runner SHALL set the working directory of the Child_Server_Process to the Base_Runtime_Directory so that files created by the Child_Server_Process are written under the Base_Runtime_Directory.
2. WHEN the Server_Runner performs the Shadow_Run, THE Server_Runner SHALL set the working directory of the Child_Server_Process to the Base_Runtime_Directory.
3. WHEN the Server_Runner performs the blocking server execution, THE Server_Runner SHALL set the working directory of the Child_Server_Process to the Base_Runtime_Directory.
4. THE Server_Runner SHALL locate the server jar for launch as a path relative to the Base_Runtime_Directory.
5. IF the Child_Server_Process cannot be started because the Base_Runtime_Directory does not exist or the server jar is absent from the Base_Runtime_Directory, THEN THE Server_Runner SHALL abort the launch and report an error indicating that the working directory or server jar could not be resolved.

### Requirement 6: Prefer standard-library NIO file I/O

**User Story:** As a maintainer, I want file and path operations to use the standard library, so that the wrapper stays cross-platform without new dependencies.

#### Acceptance Criteria

1. WHEN the Wrapper reads, writes, copies, or checks the existence of a file, THE Wrapper SHALL perform the operation through NIO_File_IO.
2. THE Wrapper SHALL declare no third-party dependency whose purpose is base-directory resolution or file I/O in its build dependency declarations.
3. WHERE a path is joined to the Base_Runtime_Directory, THE Wrapper SHALL construct the resulting path using NIO_File_IO path-joining and SHALL NOT use a hardcoded platform separator character in the joined path expression.
4. WHEN the Wrapper creates a directory or file under the Base_Runtime_Directory, THE Wrapper SHALL create it through NIO_File_IO.

### Requirement 7: Preserve all existing wrapper behaviors

**User Story:** As an operator, I want every current behavior to keep working after the refactor, so that the change is transparent to how I run the server.

#### Acceptance Criteria

1. WHEN the required configuration files (`server.properties` and the Geyser `config.yml`) already exist under the Base_Runtime_Directory, THE Wrapper SHALL skip the Shadow_Run.
2. WHEN a required configuration file is absent under the Base_Runtime_Directory, THE Wrapper SHALL perform the Shadow_Run to generate default configuration files.
3. WHEN the EULA file is absent, THE Server_Loader SHALL accept the EULA automatically.
4. IF a bundled plugin resource is missing from the classpath, THEN THE Plugins_Loader SHALL print a report containing the plugin name and official download link and SHALL halt startup.
5. THE Server_Config SHALL enforce the Bedrock-critical settings `server-port=25565` and `online-mode=true`.
6. WHERE an environment variable supplies a value for an enforced Bedrock-critical setting, THE Server_Config SHALL apply the enforced value rather than the environment-variable value.
7. WHEN the Wrapper starts the server execution phase, THE Network_Reporter SHALL print the startup network report.

### Requirement 8: Handle base-directory edge and error cases

**User Story:** As an operator, I want clear failures when the environment cannot support the base directory, so that I can diagnose problems instead of hitting silent misbehavior.

#### Acceptance Criteria

1. IF the User_Home value is unset or blank, THEN THE Directory_Resolver SHALL halt startup with a non-zero exit, provision no downstream artifacts, and emit an error message identifying the missing user home.
2. IF the Base_Runtime_Directory cannot be created, THEN THE Directory_Resolver SHALL halt startup with a non-zero exit, provision no downstream artifacts, and emit an error message identifying the base directory path.
3. IF the Base_Runtime_Directory exists but is not writable, THEN THE Directory_Resolver SHALL halt startup with a non-zero exit, provision no downstream artifacts, and emit an error message identifying the base directory path.
4. THE Directory_Resolver SHALL perform the User_Home, creation, and writability checks before the Wrapper provisions any artifact under the Base_Runtime_Directory, so that a failed check leaves the filesystem unmodified by later phases.
5. WHERE the resolved Base_Runtime_Directory path contains space characters, THE Wrapper SHALL provision artifacts, edit configuration, and launch the Child_Server_Process with the same successful outcomes as for a path without spaces.

### Requirement 9: Remain correct across supported platforms

**User Story:** As an operator, I want the wrapper to work on Windows, macOS, and Linux including the Docker target, so that cross-platform hosting is genuinely portable.

#### Acceptance Criteria

1. WHERE the Wrapper runs on Windows, macOS, or Linux, THE Wrapper SHALL resolve the Base_Runtime_Directory from the user home and provision all artifacts under it.
2. WHERE the Wrapper runs inside the Docker Temurin Java 21 Linux environment, THE Wrapper SHALL resolve the Base_Runtime_Directory from the container user home and provision under it.
3. IF the host operating system is Windows, THEN THE Wrapper SHALL resolve the child JVM launcher to the Windows `java.exe` binary, otherwise THE Wrapper SHALL resolve it to the Unix `java` binary.
4. THE Wrapper SHALL target Java 21 for all refactored code.

### Requirement 10: Update steering documentation to reflect the new runtime location

**User Story:** As a maintainer, I want the steering documents updated to describe the new base directory, so that the documented runtime model matches the implementation.

#### Acceptance Criteria

1. THE `product.md` steering document SHALL describe the provisioning location as the per-OS Base_Runtime_Directory resolved to `<user.home>/mclovers`, and SHALL NOT retain any prior description that places provisioning in a project-relative location.
2. THE `structure.md` steering document SHALL describe the runtime working directory as the Base_Runtime_Directory resolved to `<user.home>/mclovers` rather than a `minecraft_server/` directory created in the project area.
3. THE `tech.md` steering document SHALL describe the child-process working directory as the Base_Runtime_Directory resolved to `<user.home>/mclovers` rather than a CWD-relative location.
