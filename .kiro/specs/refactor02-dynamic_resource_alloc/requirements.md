# Requirements Document

## Introduction

The Minecraft server wrapper (`mc-lovers`) currently bundles fixed-version jar files as classpath resources: a server jar (`server.jar`, with alternate flavors `server-mojang.jar`, `server-paper.jar`, `server-purpur.jar`) at the resources root, and four plugin jars (`Geyser-Spigot.jar`, `floodgate-spigot.jar`, `ViaVersion.jar`, `ViaBackwards.jar`) under `plugins/`. Because those jars are frozen at build time, a Minecraft version increase produces a client-server version mismatch that takes the server down until the bundle is rebuilt.

This feature refactors provisioning so that every run, including the throwaway Shadow_Run, obtains the latest versioned jars over the internet rather than extracting frozen jars from the classpath. On each run the wrapper wipes and recreates a dedicated staging directory under the User_Home, downloads the latest server jar and the four required plugin jars into it, recognizes each downloaded jar by its version-tagged filename using a regular expression, and copies (injects) the recognized jars into the Base_Runtime_Directory (also under the User_Home) as the execution server jar and its `plugins/` tree, preserving each downloaded file's original filename, so both the Shadow_Run and the real run use the latest jars. The names of both directories are supplied by the shared `.properties` configuration rather than hardcoded in source: the base directory name (default `mclovers`) and the staging directory name (default `resources`) are read from configuration, with the base directory name fed to the prior refactor's directory resolver. Because the injected server jar keeps its original versioned filename, the Server_Runner launches that recognized filename rather than a fixed name. The classpath resources directory (`app/src/main/resources`) no longer carries jars; it holds only the Java-standard `.properties` configuration file that supplies the directory names, download URLs, and related raw strings.

Failure is fatal by design: when the internet is unavailable, a download fails, an expected jar cannot be recognized, or the configuration is missing or invalid, the wrapper fails fast with a clear, actionable error and a non-zero exit, and nothing stale is executed. Running a frozen jar would reintroduce the exact version-mismatch downtime this refactor exists to prevent. All existing downstream behaviors are preserved once jars are provisioned, and the wrapper remains correct on Windows, macOS, Linux, and the Docker Temurin Java 21 target using `java.nio.file` and the JDK HTTP client, with no heavy new dependency.

## Glossary

- **Wrapper**: The `mc-lovers` application (entry point `minecraft.wrapper.App`) that provisions and launches the Minecraft server as a child process.
- **Base_Runtime_Directory**: The stable per-OS execution root for all runtime artifacts, resolved to `<user.home>/<base-dir-name>` where the base directory name (default `mclovers`) is supplied by the Download_Config. Established by the prior refactor. Referred to below as the base directory.
- **Staging_Directory**: The per-OS download staging root, resolved to `<user.home>/<staging-dir-name>` where the staging directory name (default `resources`) is supplied by the Download_Config, wiped and recreated on every run to hold freshly downloaded versioned jars before injection.
- **User_Home**: The value of the Java system property `user.home`.
- **Resource_Provisioner**: The Wrapper component responsible for wiping and recreating the Staging_Directory, downloading jars, recognizing them, and injecting them into the Base_Runtime_Directory.
- **Download_Config**: The Java-standard `.properties` file, bundled under `app/src/main/resources`, that supplies the base directory name, the staging directory name, download URLs, and related raw string settings to the Wrapper and the Resource_Provisioner. It is the single source of truth for both directory names.
- **Server_Jar**: The Minecraft server jar. After injection it retains its original downloaded filename (its Versioned_Filename) under the Base_Runtime_Directory, and the Server_Runner launches it by that name.
- **Plugin_Jar**: One of the four required plugin jars (Geyser, Floodgate, ViaVersion, ViaBackwards) installed under the `plugins` subdirectory of the Base_Runtime_Directory, retaining its original downloaded filename.
- **Original_Filename**: The exact filename of a downloaded jar as recognized in the Staging_Directory, preserved unchanged when the jar is injected into the Base_Runtime_Directory.
- **Directory_Name_Config**: The two Download_Config keys that name the runtime directories: the base directory name key and the staging directory name key. Both are required and non-blank.
- **Versioned_Filename**: A downloaded jar filename that carries a version string (for example `purpur-v21...`), matched by a regular expression to recognize and classify the jar.
- **Jar_Recognizer**: The Resource_Provisioner subcomponent that selects and classifies staged jars by matching Versioned_Filename patterns via regular expression.
- **HTTP_Client**: The JDK standard `java.net.http.HttpClient` used to perform downloads, following redirects.
- **Server_Loader**: The component that provisions the server environment (directory creation, EULA acceptance) under the Base_Runtime_Directory.
- **Plugins_Loader**: The component that installs Plugin_Jar files into the `plugins` subdirectory of the Base_Runtime_Directory.
- **Server_Runner**: The component that launches and manages the Minecraft server child JVM process.
- **Server_Config**: The component that reads, modifies, and saves `server.properties`.
- **Geyser_Config**: The component that edits the existing Geyser `config.yml` in place.
- **Network_Reporter**: The component that prints the startup network report.
- **Shadow_Run**: A throwaway server start that generates default configuration files, then stops.
- **Child_Server_Process**: The separate Minecraft server JVM process spawned by the Server_Runner.
- **NIO_File_IO**: The `java.nio.file` API (`Path`, `Paths`, `Files`).

## Requirements

### Requirement 1: Wipe and recreate the staging directory each run

**User Story:** As an operator, I want a fresh staging directory prepared on every launch, so that stale downloads from prior runs never contaminate the jars selected for execution.

#### Acceptance Criteria

1. THE Resource_Provisioner SHALL resolve the Staging_Directory as the child of the User_Home value returned by `System.getProperty("user.home")` named by the staging directory name key read from the Download_Config, using NIO_File_IO path joining without a hardcoded platform separator character.
2. WHEN the Wrapper starts a run, THE Resource_Provisioner SHALL recursively delete the existing Staging_Directory and all of its contents using NIO_File_IO before any download begins, such that upon successful completion zero files or subdirectories remain under the Staging_Directory path.
3. WHEN the Resource_Provisioner has deleted the Staging_Directory, THE Resource_Provisioner SHALL recreate the Staging_Directory including any missing parent directories using NIO_File_IO, such that the recreated Staging_Directory exists and contains zero entries.
4. IF the Staging_Directory does not exist at the start of a run, THEN THE Resource_Provisioner SHALL treat the recursive delete as a no-op and proceed to recreate the empty Staging_Directory.
5. THE Resource_Provisioner SHALL complete the wipe-and-recreate of the Staging_Directory before the Shadow_Run and before the blocking server execution on every run.
6. IF recursive deletion or recreation of the Staging_Directory fails, including the case where an entry cannot be deleted because it is locked or in use by another process, THEN THE Resource_Provisioner SHALL halt startup with a non-zero exit and produce an error indication reporting the Staging_Directory path and the failure cause, with no jar downloaded or injected and no partially prepared Staging_Directory left in use for the current run.
7. IF the wipe-and-recreate of the Staging_Directory does not complete within 30 seconds, THEN THE Resource_Provisioner SHALL halt startup with a non-zero exit and produce an error indication reporting the Staging_Directory path and the timeout as the failure cause, with no jar downloaded or injected.

### Requirement 2: Download the latest jars over the internet each run

**User Story:** As an operator, I want the latest server and plugin jars fetched on every run, so that the server always matches current Minecraft client versions without a rebuild.

#### Acceptance Criteria

1. WHEN the Staging_Directory has been recreated, THE Resource_Provisioner SHALL download the latest Server_Jar over the internet into the Staging_Directory using the HTTP_Client.
2. WHEN the Staging_Directory has been recreated, THE Resource_Provisioner SHALL download the latest of each of the four required Plugin_Jar artifacts (Geyser, Floodgate, ViaVersion, ViaBackwards) over the internet into the Staging_Directory using the HTTP_Client.
3. THE Resource_Provisioner SHALL perform the downloads on every run, including before the Shadow_Run and before the blocking server execution.
4. WHEN a download source responds with an HTTP redirect, THE HTTP_Client SHALL follow the redirect to the final resource, up to a maximum of 5 chained redirects.
5. THE Resource_Provisioner SHALL derive each download target URL from the Download_Config.
6. WHEN a download completes, THE Resource_Provisioner SHALL write the downloaded bytes to a file within the Staging_Directory using NIO_File_IO.
7. IF a download request does not complete within 30 seconds of connection or data transfer, THEN THE Resource_Provisioner SHALL halt startup with a non-zero exit and produce an error indication reporting the artifact name and that connectivity is required, with no jar injected.
8. IF a download request returns a non-success HTTP status, THEN THE Resource_Provisioner SHALL halt startup with a non-zero exit and produce an error indication reporting the artifact name, the requested URL, and the returned status, with no jar injected.

### Requirement 3: Recognize and classify staged jars by version-tagged filename

**User Story:** As an operator, I want the wrapper to identify each downloaded jar by its version-tagged name, so that the correct server jar and each plugin are selected despite changing version strings.

#### Acceptance Criteria

1. THE Jar_Recognizer SHALL select the Server_Jar from the Staging_Directory by matching its Versioned_Filename against a server-jar regular expression.
2. THE Jar_Recognizer SHALL select each Plugin_Jar from the Staging_Directory by matching its Versioned_Filename against a plugin-specific regular expression, distinguishing each of the four required plugins (Geyser, Floodgate, ViaVersion, and ViaBackwards) from one another.
3. IF a staged file's Versioned_Filename matches both the server-jar regular expression and any plugin-specific regular expression, THEN THE Jar_Recognizer SHALL classify it as the Server_Jar and SHALL NOT classify it as a Plugin_Jar, so that the Server_Jar is never installed as a plugin and no Plugin_Jar is launched as the Server_Jar.
4. IF no staged file matches the server-jar regular expression, THEN THE Jar_Recognizer SHALL halt startup with a non-zero exit, leave all staged files in the Staging_Directory unmodified, and produce an error indication reporting that the Server_Jar could not be recognized and naming the Staging_Directory.
5. IF no staged file matches the regular expression for any one of the four required plugins (Geyser, Floodgate, ViaVersion, ViaBackwards), THEN THE Jar_Recognizer SHALL halt startup with a non-zero exit, inject no jar, leave all staged files in the Staging_Directory unmodified, and produce an error indication naming the unrecognized plugin.
6. IF more than one staged file matches the regular expression for a single required artifact, THEN THE Jar_Recognizer SHALL select the single file whose Versioned_Filename is first in case-sensitive lexicographic (ASCII) ascending order and SHALL record which staged file was chosen.

### Requirement 4: Inject recognized jars into the base runtime directory

**User Story:** As an operator, I want the recognized jars copied into the execution directory, so that both the shadow run and the real run launch from the latest jars.

#### Acceptance Criteria

1. WHEN the Server_Jar has been recognized in the Staging_Directory, THE Resource_Provisioner SHALL copy it into the Base_Runtime_Directory under its Original_Filename using NIO_File_IO, overwriting any existing file of that same Original_Filename.
2. WHEN each Plugin_Jar has been recognized in the Staging_Directory, THE Resource_Provisioner SHALL copy it into the `plugins` subdirectory of the Base_Runtime_Directory under its Original_Filename using NIO_File_IO, overwriting any existing plugin file of that same Original_Filename.
3. WHEN the `plugins` subdirectory of the Base_Runtime_Directory is absent at injection time, THE Resource_Provisioner SHALL create it using NIO_File_IO before copying Plugin_Jar files.
4. THE Resource_Provisioner SHALL complete injection of the Server_Jar and all four Plugin_Jar files into the Base_Runtime_Directory before the Shadow_Run and before the blocking server execution.
5. THE Resource_Provisioner SHALL preserve the Original_Filename of the injected Server_Jar and each injected Plugin_Jar unchanged, and SHALL provide the injected Server_Jar's Original_Filename to the Server_Runner so that the Server_Runner launches that exact filename via its `-jar` invocation rather than a fixed name.
6. IF a recognized Server_Jar or Plugin_Jar source file is absent from the Staging_Directory at injection time, THEN THE Resource_Provisioner SHALL halt startup with a non-zero exit and produce an error indication naming the missing source file, with no Child_Server_Process launched.
7. IF copying a recognized Server_Jar or Plugin_Jar into the Base_Runtime_Directory fails, THEN THE Resource_Provisioner SHALL halt startup with a non-zero exit and produce an error indication reporting the source and destination paths, with no Child_Server_Process launched.

### Requirement 5: Store configuration as properties and remove bundled jars

**User Story:** As a maintainer, I want download URLs and raw strings held in a properties file and the jars removed from resources, so that resources carry only configuration and jars are never frozen at build time.

#### Acceptance Criteria

1. THE Wrapper SHALL read download URLs and related raw string settings from the Download_Config, a Java-standard `.properties` file bundled under `app/src/main/resources` and loaded from the classpath.
2. THE `app/src/main/resources` directory SHALL NOT contain any Server_Jar file (`server.jar`, `server-mojang.jar`, `server-paper.jar`, `server-purpur.jar`).
3. THE `app/src/main/resources/plugins` directory SHALL NOT contain any Plugin_Jar file (`Geyser-Spigot.jar`, `floodgate-spigot.jar`, `ViaVersion.jar`, `ViaBackwards.jar`).
4. WHEN the Resource_Provisioner requires a download URL or configurable raw string, THE Resource_Provisioner SHALL obtain the value from the Download_Config rather than from a hardcoded literal in compiled code.
5. IF the Download_Config resource is absent from the classpath, THEN THE Resource_Provisioner SHALL halt startup before any Server_Jar or Plugin_Jar download is attempted, terminate with a non-zero exit code, and produce an error indication reporting that the configuration resource is missing.
6. IF the Download_Config is present but a required key is absent, THEN THE Resource_Provisioner SHALL halt startup before any Server_Jar or Plugin_Jar download is attempted, terminate with a non-zero exit code, and produce an error indication naming the absent key.
7. IF the Download_Config is present but a required key holds a value that is empty or contains only whitespace characters, THEN THE Resource_Provisioner SHALL halt startup before any Server_Jar or Plugin_Jar download is attempted, terminate with a non-zero exit code, and produce an error indication naming the blank key.
8. THE Wrapper SHALL read the base directory name and the staging directory name from the Directory_Name_Config keys in the Download_Config rather than from a hardcoded directory-name literal in compiled code.
9. THE Wrapper SHALL provide the base directory name read from the Download_Config to the prior refactor's directory resolver so that the Base_Runtime_Directory is resolved as `<user.home>/<base-dir-name>`, and SHALL provide the staging directory name to the Resource_Provisioner so that the Staging_Directory is resolved as `<user.home>/<staging-dir-name>`.
10. IF a Directory_Name_Config key is absent, empty, or contains only whitespace characters, THEN THE Wrapper SHALL halt startup before resolving or creating any directory, terminate with a non-zero exit code, and produce an error indication naming the offending directory-name key.
11. WHERE the Download_Config supplies a base directory name and a staging directory name, THE Wrapper SHALL treat the Download_Config as the single source of truth for both names, and the compiled defaults (`mclovers` for the base directory and `resources` for the staging directory) SHALL serve only as documentation of the intended default values shipped in the Download_Config.

### Requirement 6: Fail fast with nothing stale executed

**User Story:** As an operator, I want the wrapper to stop immediately with a clear error when provisioning cannot complete, so that a version-mismatched or stale server is never started.

#### Acceptance Criteria

1. IF a download attempt does not establish connectivity or receive any data within 30 seconds, THEN THE Resource_Provisioner SHALL halt startup with a non-zero exit code and produce an error indication reporting the failed artifact and that connectivity is required, with no Child_Server_Process launched and the prior runtime state left unchanged.
2. IF any download fails after 3 attempts for the same artifact, THEN THE Resource_Provisioner SHALL NOT fall back to a previously downloaded or bundled jar and SHALL halt startup with a non-zero exit code.
3. IF any required artifact in the Staging_Directory cannot be recognized, where recognition requires the artifact to be present, fully written, and to match its expected identity, THEN THE Resource_Provisioner SHALL halt startup with a non-zero exit code before the Shadow_Run and before the blocking server execution.
4. WHEN provisioning fails at any step, THE Resource_Provisioner SHALL remove every partially written or unrecognized artifact from both the Staging_Directory and the Base_Runtime_Directory so that no such artifact remains to be executed on the failed run.
5. THE Resource_Provisioner SHALL complete the wipe, download, recognition, and injection successfully before the Wrapper performs the Shadow_Run or the blocking server execution, so that a provisioning failure at any of those steps prevents both.
6. WHILE a download is in progress, THE Resource_Provisioner SHALL write the artifact such that an interrupted download does not leave a recognized artifact in the Staging_Directory.

### Requirement 7: Verify download integrity and clean partial downloads

**User Story:** As an operator, I want downloaded jars validated and failed downloads cleaned up, so that a corrupt or truncated file is never selected for execution.

#### Acceptance Criteria

1. WHEN a download completes, THE Resource_Provisioner SHALL verify that the downloaded file exists in the Staging_Directory and has a size greater than zero bytes before marking the artifact as available for injection.
2. IF a downloaded file has a size of zero bytes, THEN THE Resource_Provisioner SHALL treat the download as failed, delete the empty file from the Staging_Directory using NIO_File_IO, and halt startup with a non-zero exit, producing an error indication identifying the failed artifact.
3. IF a download terminated before all expected bytes were written, THEN THE Resource_Provisioner SHALL treat the download as failed, delete the partial file from the Staging_Directory using NIO_File_IO, and halt startup with a non-zero exit, producing an error indication identifying the failed artifact.
4. WHEN deleting a failed or partial file from the Staging_Directory, THE Resource_Provisioner SHALL confirm the file no longer exists before halting.
5. IF deletion of a failed or partial file from the Staging_Directory does not succeed, THEN THE Resource_Provisioner SHALL still halt startup with a non-zero exit and produce an error indication reporting both the download failure and the deletion failure.
6. WHEN a recognized Server_Jar or Plugin_Jar is about to be injected into the Base_Runtime_Directory, THE Resource_Provisioner SHALL verify that the recognized file exists and has a size greater than zero bytes prior to injection.
7. IF a recognized Server_Jar or Plugin_Jar fails the pre-injection non-empty verification, THEN THE Resource_Provisioner SHALL halt startup with a non-zero exit, inject no jar into the Base_Runtime_Directory, and produce an error indication identifying the failed artifact.

### Requirement 8: Preserve all existing downstream wrapper behaviors

**User Story:** As an operator, I want every current behavior to keep working once the latest jars are provisioned, so that the change is transparent to how I run the server.

#### Acceptance Criteria

1. WHEN the injected Server_Jar and Plugin_Jar files are in place and the EULA file is absent under the Base_Runtime_Directory, THE Server_Loader SHALL create the EULA file under the Base_Runtime_Directory with the content `eula=true`.
2. IF the EULA file already exists under the Base_Runtime_Directory, THEN THE Server_Loader SHALL preserve the existing EULA file without overwriting it.
3. WHEN both required configuration files (`server.properties` and the Geyser `config.yml`) exist under the Base_Runtime_Directory, THE Wrapper SHALL skip the Shadow_Run.
4. WHEN at least one required configuration file (`server.properties` or the Geyser `config.yml`) is absent under the Base_Runtime_Directory, THE Wrapper SHALL perform the Shadow_Run using the injected Server_Jar and Plugin_Jar to generate the default configuration files.
5. THE Server_Config SHALL enforce the Bedrock-critical settings `server-port=25565` and `online-mode=true`.
6. WHERE an environment variable supplies a value for an enforced Bedrock-critical setting, THE Server_Config SHALL persist the enforced value rather than the environment-variable value.
7. WHERE the `mtu` key and the `auth-type` key are present in the existing Geyser `config.yml`, THE Geyser_Config SHALL edit those keys in place, setting `mtu` to `1200` and `auth-type` to `floodgate`.
8. IF an expected Geyser configuration key (`mtu` or `auth-type`) is absent from the existing Geyser `config.yml`, THEN THE Geyser_Config SHALL skip editing that key, emit a warning identifying the missing key, and leave the file otherwise unchanged, and IF the Geyser `config.yml` is absent THEN THE Geyser_Config SHALL skip all editing and emit a warning without creating the file.
9. WHEN the Wrapper enters the server execution phase, THE Network_Reporter SHALL print the startup network report.
10. WHEN the Server_Runner starts the Child_Server_Process for either the Shadow_Run or the blocking execution, THE Server_Runner SHALL set the working directory of the Child_Server_Process to the Base_Runtime_Directory.
11. WHILE the Shadow_Run is stopping, IF the Child_Server_Process does not exit within 60 seconds after the stop command is sent, THEN THE Server_Runner SHALL forcibly terminate the Child_Server_Process together with its descendant process tree.
12. WHEN the Wrapper shuts down, THE Server_Runner SHALL request termination of the Child_Server_Process together with its descendant process tree, and IF the process tree does not exit within 10 seconds THEN THE Server_Runner SHALL forcibly terminate the Child_Server_Process together with its descendant process tree.
13. WHEN the Server_Runner launches the Child_Server_Process for either the Shadow_Run or the blocking execution, THE Server_Runner SHALL launch the injected Server_Jar by the Original_Filename provided by the Resource_Provisioner rather than a fixed `server.jar` name.

### Requirement 9: Prefer the standard library and keep dependencies declared in the catalog

**User Story:** As a maintainer, I want provisioning built on the JDK standard library, so that the wrapper stays lightweight and cross-platform without a heavy new dependency.

#### Acceptance Criteria

1. THE Resource_Provisioner SHALL perform HTTP downloads through the HTTP_Client backed by the JDK standard `java.net.http.HttpClient`.
2. THE Resource_Provisioner SHALL perform all file deletion, creation, copy, and existence operations through NIO_File_IO.
3. THE Resource_Provisioner SHALL parse the Download_Config using the Java-standard `java.util.Properties` API.
4. WHERE a new third-party dependency is required, THE Wrapper SHALL declare its coordinate and version in the Gradle version catalog at `gradle/libs.versions.toml` and reference it through the `libs` accessor rather than hardcoding the coordinate in a build script.
5. THE Wrapper SHALL target Java 21 for all code added or changed by this feature.
6. IF an HTTP download via the HTTP_Client fails to complete or returns a non-success response, THEN THE Resource_Provisioner SHALL abort the affected download, leave no partially written destination file in place, and surface an error indicating that the download failed.
7. IF the Download_Config is absent or cannot be parsed by the `java.util.Properties` API, THEN THE Resource_Provisioner SHALL halt provisioning and surface an error indicating that the Download_Config is missing or malformed.

### Requirement 10: Remain correct across supported platforms

**User Story:** As an operator, I want provisioning to work on Windows, macOS, Linux, and the Docker target, so that cross-platform hosting stays genuinely portable.

#### Acceptance Criteria

1. WHERE the Wrapper runs on Windows, macOS, or Linux, THE Resource_Provisioner SHALL resolve the Staging_Directory from the User_Home, wipe and recreate it, download the latest jars, and inject them into the Base_Runtime_Directory.
2. WHERE the Wrapper runs inside the Docker Temurin Java 21 Linux environment, THE Resource_Provisioner SHALL resolve the Staging_Directory from the container User_Home and provision under it.
3. WHERE the resolved Staging_Directory or Base_Runtime_Directory path contains one or more space characters, THE Resource_Provisioner SHALL complete the wipe, download, recognition, and injection steps and reach the same completion state as for an otherwise identical path without spaces.
4. THE Resource_Provisioner SHALL construct every Staging_Directory and Base_Runtime_Directory path using NIO_File_IO path joining, and SHALL NOT embed a hardcoded platform separator character in any joined path expression.
5. IF the User_Home cannot be resolved on Windows, macOS, Linux, or the Docker Temurin Java 21 Linux environment, THEN THE Resource_Provisioner SHALL abort provisioning, leave the Base_Runtime_Directory unchanged, and emit an error indication reporting that the User_Home could not be resolved.
6. IF path resolution, wipe, download, or injection fails on any supported platform, THEN THE Resource_Provisioner SHALL abort provisioning, preserve any existing Base_Runtime_Directory contents, and emit an error indication identifying the failed step.

### Requirement 11: Update steering documentation to reflect dynamic provisioning

**User Story:** As a maintainer, I want the steering documents updated to describe download-based provisioning, so that the documented model matches the implementation.

#### Acceptance Criteria

1. THE `product.md` steering document SHALL describe the server and plugin jars as downloaded fresh over the internet into the Staging_Directory (default `<user.home>/resources`, name supplied by the Download_Config) on every run and injected into the Base_Runtime_Directory (default `<user.home>/mclovers`, name supplied by the Download_Config).
2. THE `product.md` steering document SHALL NOT retain any statement describing the bundled artifact as carrying the server or plugin jars as classpath resources.
3. THE `structure.md` steering document SHALL describe `app/src/main/resources` as holding only the Download_Config `.properties` file (which supplies the directory names, download URLs, and patterns), and SHALL NOT describe server jars at the resources root or plugin jars under `resources/plugins`.
4. THE `tech.md` steering document SHALL describe the server and plugins as obtained over the internet on every run rather than shipped inside the wrapper as classpath resources.
5. THE `tech.md` steering document SHALL describe the fail-fast behavior that halts startup with an error indication when provisioning cannot complete, including when the download source is unreachable or a downloaded artifact is absent.
