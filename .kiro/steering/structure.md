---
inclusion: always
---

# Structure

## Top-level layout

- `app/` holds the single Gradle module and all wrapper source, tests, and bundled resources.
- `gradle/` holds the version catalog (`libs.versions.toml`), the Gradle wrapper, and a deprecated `jpackage.gradle.kts` placeholder.
- `docs/` holds supporting material, including a Java server flavor comparison, feedback notes, and a LaTeX plus PDF write-up.
- `.github/workflows/` holds the CI definitions: `test.yml` for the test run and `release.yml` for the multi-platform release build.
- `.kiro/` holds agent configuration: `steering/`, `agents/`, `hooks/`, `skills/`, and `settings/`.
- `.agents/` holds the authoritative rule files and workflows referenced by `AGENTS.md`. Repository conventions live there, and `AGENTS.md` is the index that points to them.
- `Dockerfile` defines the Linux build and run image. The `*.bat` scripts at the root are Windows developer-environment helpers and are not part of the server runtime.

## Source code

All wrapper source lives under `app/src/main/java/minecraft/wrapper/`. The package is `minecraft.wrapper`. The classes divide by responsibility as follows.

- `App.java` is the entry point and orchestrator. It runs the lifecycle in phases: install loaders, register a centralized shutdown hook, conditionally perform the shadow run, apply configuration overrides, print the network report, and execute the server. Cross-cutting lifecycle changes belong here.
- `ServerLoader.java` provisions the server environment: it ensures the working directory exists, extracts the server jar from resources when absent, and writes an accepted EULA file.
- `PluginsLoader.java` installs the bundled plugins from classpath resources into the `plugins` directory and fails with a download-link report when an expected plugin resource is missing.
- `ServerRunner.java` is a singleton that manages the server child process. It provides the shadow run (start, wait for the completion log line, send `stop`), the blocking `execute` run, and process-tree termination.
- `ServerConfig.java` reads, modifies, and saves `server.properties`, bridging environment variables into server settings and enforcing Bedrock-critical values.
- `GeyserConfig.java` edits the existing Geyser `config.yml` in place, adjusting MTU and authentication type, and never creates the file from scratch.
- `NetworkReporter.java` prints the startup table describing ports and authentication per edition.

## Bundled resources

`app/src/main/resources/` carries the artifacts that are shipped inside the wrapper.

- `plugins/` contains the Geyser, Floodgate, ViaVersion, and ViaBackwards jars, keyed by the exact filenames that `PluginsLoader` expects.
- Server jars sit at the resources root. `server.jar` is the one extracted at runtime, and additional candidate server flavors are kept alongside it.

When a plugin or server jar must be added or replaced, the file is placed here under the expected name and the project is rebuilt so the resource is packaged into the bundle.

## Tests

Tests live under `app/src/test/java/minecraft/wrapper/` and mirror the main package. They use JUnit 4. New tests are placed in this tree and follow the existing style.

## Runtime and build outputs

These directories are generated, not authored, and are not committed as source.

- `minecraft_server/` is created at runtime as the server working directory. It receives the extracted jar, `eula.txt`, `server.properties`, and the `plugins/` tree with generated plugin configs. The clean task removes it.
- `app/build/` holds Gradle output, including `install/` from `installDist` and `dist/` from `jpackage`.

## Where changes usually go

- Server lifecycle or phase ordering: `App.java`.
- Server or plugin provisioning: `ServerLoader.java` and `PluginsLoader.java`.
- Process management or launch flags: `ServerRunner.java`.
- Server or Bedrock settings and defaults: `ServerConfig.java` and `GeyserConfig.java`.
- Build, dependencies, or packaging: `app/build.gradle.kts` and `gradle/libs.versions.toml`.
- Adding or replacing a bundled jar: `app/src/main/resources/`.
