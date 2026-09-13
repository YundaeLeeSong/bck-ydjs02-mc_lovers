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

- `App.java` is the entry point and orchestrator. It runs the lifecycle in phases: resolve the Base Runtime Directory (Phase 0), install loaders, register a centralized shutdown hook, conditionally perform the shadow run, apply configuration overrides, print the network report, and execute the server. Cross-cutting lifecycle changes belong here.
- `RuntimeDirectory.java` resolves, creates, and validates the per-OS Base Runtime Directory at `<user.home>/mclovers` as the first startup phase, independent of the current working directory. `RuntimeDirectoryException.java` is the checked failure it raises, carrying the offending path and cause so `App` can report it and exit non-zero.
- `ServerLoader.java` provisions the server environment: it ensures the working directory exists and writes an accepted EULA file. The server jar itself is downloaded and injected by the provisioner rather than extracted from resources.
- `ResourceProvisioner.java` resolves and wipes the Staging Directory, downloads the latest server jar and the four plugin jars over the internet each run, recognizes them by version-tagged filename via config regex, and injects them into the Base Runtime Directory (server jar at the root, plugins under `plugins/`) under their original downloaded filenames.
- `ServerRunner.java` is a singleton that manages the server child process. It provides the shadow run (start, wait for the completion log line, send `stop`), the blocking `execute` run, and process-tree termination.
- `ServerConfig.java` reads, modifies, and saves `server.properties`, bridging environment variables into server settings and enforcing Bedrock-critical values.
- `GeyserConfig.java` edits the existing Geyser `config.yml` in place, adjusting MTU and authentication type, and never creates the file from scratch.
- `NetworkReporter.java` prints the startup table describing ports and authentication per edition.

## Bundled resources

`app/src/main/resources/` no longer carries any jars. It holds only the `download.properties` Download Config, which supplies the two runtime directory names, the download URLs for the server jar and the four plugin jars, and the regex patterns used to recognize each downloaded jar by its version-tagged filename.

When a download source, recognition pattern, or directory name must change, `download.properties` is edited and the project is rebuilt so the updated config is packaged into the bundle.

## Tests

Tests live under `app/src/test/java/minecraft/wrapper/` and mirror the main package. They use JUnit 4. New tests are placed in this tree and follow the existing style.

## Runtime and build outputs

These directories are generated, not authored, and are not committed as source.

- The Base Runtime Directory, resolved per-OS to `<user.home>/mclovers` (name from the Download Config), is created at runtime as the server working directory. It receives the injected server jar, `eula.txt`, `server.properties`, and the `plugins/` tree with generated plugin configs. Because it lives under the user home rather than the project area, it is not affected by the project's clean task.
- The Staging Directory, resolved to `<user.home>/resources` (name from the Download Config), is wiped and recreated each run to hold the freshly downloaded jars before they are injected into the Base Runtime Directory. It too lives under the user home and is outside the clean task.
- `app/build/` holds Gradle output, including `install/` from `installDist` and `dist/` from `jpackage`.

## Where changes usually go

- Server lifecycle or phase ordering: `App.java`.
- Server or plugin provisioning: `ResourceProvisioner.java` and `ServerLoader.java`.
- Process management or launch flags: `ServerRunner.java`.
- Server or Bedrock settings and defaults: `ServerConfig.java` and `GeyserConfig.java`.
- Build, dependencies, or packaging: `app/build.gradle.kts` and `gradle/libs.versions.toml`.
- Changing a download source, recognition pattern, or directory name: `app/src/main/resources/download.properties`.
