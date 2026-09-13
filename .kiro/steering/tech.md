---
inclusion: always
---

# Tech

## Language and runtime

- Java is the implementation language. The Gradle toolchain pins the language version to Java 21, which is also the minimum required by modern Minecraft server versions (1.20.5 and later). New Java code targets Java 21 and existing standard-library patterns are preferred over new dependencies.
- The wrapper spawns the actual Minecraft server as a separate child JVM process. It locates the `java` executable under `java.home`, adjusting the binary name per operating system, and launches the server jar with fixed heap flags. The child process runs with its working directory set to the Base Runtime Directory, resolved per-OS to `<user.home>/mclovers` rather than a CWD-relative location, so all server-generated files land there. This child-process model is central and must be preserved when the runtime is touched.

## Build system

- Gradle with the Kotlin DSL is the build system, driven through the Gradle wrapper. Build commands are run as `./gradlew <task>` on Unix shells and `gradlew` on Windows, never against a globally installed Gradle.
- Dependency coordinates and versions are declared in the Gradle version catalog at `gradle/libs.versions.toml`. New dependencies are added there and referenced through the `libs` accessor rather than hardcoded in build scripts.
- The module `app` uses the Gradle `application` plugin, with the main class set to `minecraft.wrapper.App`.
- The file `gradle/jpackage.gradle.kts` is deprecated. The packaging logic now lives in `app/build.gradle.kts`, and that is where packaging changes are made.

## Common commands

- Run the test suite with `./gradlew test`.
- Build a native application image for the current operating system with `./gradlew jpackage`. This depends on `installDist`, runs `jpackage` in `app-image` mode, and then patches a real `java` executable back into the bundled runtime because the wrapper must spawn a child JVM.
- Clean with `./gradlew clean`. The clean task is extended to remove the `dist` output and IDE `bin` output, with extra handling for files that Windows tends to lock. The Base Runtime Directory now lives under the user home (`<user.home>/mclovers`) rather than the project area, so it is outside the scope of the clean task.

## Dependencies

- Google Guava is the core utility library on the main classpath.
- JUnit 4 is the test framework. New tests follow the existing JUnit 4 style already present in the test sources.

## Packaging and distribution

- `jpackage` produces portable `app-image` bundles, named `mc-lovers`, with a low wrapper heap so the wrapper itself stays lightweight.
- Because `jpackage` strips the JDK launcher from its trimmed runtime, the build copies the platform `java` binary into the bundle at the correct per-OS runtime path and sets the executable bit on Unix systems. Any change to packaging must keep a working `java` binary inside the bundle, otherwise child-process launching breaks.

## Server and plugins

The following third-party artifacts are obtained over the internet on every run rather than shipped inside the wrapper as classpath resources, and are not built from source here. Their download URLs and filename recognition patterns come from the bundled `download.properties` Download Config.

- A Minecraft server jar. A performance-oriented flavor is used rather than vanilla, because plugin support, secure proxying, and low-RAM operation are required. Downloading the latest jar each run keeps the server matched to current clients without a rebuild.
- Geyser for Spigot, which lets Bedrock clients join the Java server.
- Floodgate for Spigot, which lets Bedrock players connect without a paid Java account.
- ViaVersion and ViaBackwards, which broaden the range of client versions that can connect.

Provisioning fails fast. When it cannot complete, startup halts with an error indication and a non-zero exit, and no child process is launched. This covers a missing or invalid Download Config, an unreachable download source, a failed or truncated download, and a downloaded artifact that cannot be recognized, so nothing stale is ever executed.

## Configuration model

- Server settings are read from environment variables at startup, with sensible defaults applied when a variable is unset. Bedrock-critical settings are enforced regardless of input, since cross-play breaks without them.
- Geyser configuration is edited in place, never generated from scratch. The wrapper lowers the network MTU for cloud environments and sets the authentication type to the Floodgate path. If the expected keys are absent, the edit is skipped with a warning rather than corrupting the file.

## Deployment targets

- A `Dockerfile` based on an Eclipse Temurin Java 21 image provides a Linux build and run environment and installs `dos2unix` so Windows-edited scripts run under Linux.
- The intended production home is a small cloud VM, with the Oracle Cloud Infrastructure free tier called out as the reference target.

## Continuous integration

- The test workflow runs `./gradlew test` on pushes and pull requests against `main`, using Temurin Java 21.
- The release workflow triggers on `v*` tags. It builds `jpackage` bundles across Windows x64, Ubuntu x64 and ARM64, macOS x64 and ARM64, and Oracle Linux x64 and ARM64, then attaches the archives to a GitHub release.
