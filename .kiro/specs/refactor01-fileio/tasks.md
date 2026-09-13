# Implementation Plan: refactor01-fileio

## Overview

This plan refactors the wrapper from a CWD-relative runtime root (`new File("minecraft_server")`) to a stable per-OS Base Runtime Directory at `<user.home>/mclovers`, resolved and validated as Phase 0 before any provisioning. All file operations move to `java.nio.file` (NIO) while backward-compatible `File` constructors are preserved so existing tests keep passing. Each step builds on the previous one, starting with the new `RuntimeDirectory` component, threading the resolved base directory through every existing component, migrating I/O to NIO, adding the launch guard, and finally wiring Phase 0 into `App.main`. Property-based and example tests validate the 10 correctness properties, and the steering docs are updated to describe the new runtime location.

Implementation language: Java 21. Build via `./gradlew`. Dependencies only from `gradle/libs.versions.toml` (Guava + JUnit 4). No new dependency is introduced.

## Tasks

- [x] 1. Create the RuntimeDirectory component and its exception
  - [x] 1.1 Implement `RuntimeDirectoryException`
    - Create `app/src/main/java/minecraft/wrapper/RuntimeDirectoryException.java`
    - Carry the offending `Path` and the underlying cause so `App.main` can print a clear message
    - Provide constructors accepting a message, and a message plus cause
    - _Requirements: 1.7, 1.8, 2.4, 2.5, 8.1, 8.2, 8.3_

  - [x] 1.2 Implement `RuntimeDirectory` with `resolve(String userHome)` and `resolveAndPrepare()`
    - Create `app/src/main/java/minecraft/wrapper/RuntimeDirectory.java` with fixed `static final String DIR_NAME = "mclovers"` (not configurable)
    - `resolve(String userHome)`: throw `RuntimeDirectoryException` when home is null or blank; otherwise return `Paths.get(userHome).resolve(DIR_NAME).toAbsolutePath().normalize()` with no filesystem side effects
    - `resolveAndPrepare()`: read `System.getProperty("user.home")`, delegate to `resolve`, then validate in order — `Files.createDirectories(base)`, verify `Files.isDirectory(base)` (else path-conflict abort with no overwrite), verify `Files.isWritable(base)` (else abort naming the path)
    - Throw `RuntimeDirectoryException` on any failed check so no downstream phase runs
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 1.7, 1.8, 2.1, 2.2, 2.4, 2.5, 6.1, 6.3, 6.4, 8.1, 8.2, 8.3, 8.4, 9.1, 9.2_

  - [ ]* 1.3 Write property test for resolution
    - **Property 1: Resolution is absolute, CWD-independent, and rooted at user.home/mclovers**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 9.1**
    - Generate >= 100 home strings (including spaces and nested paths); assert final segment `mclovers`, `isAbsolute()`, parent matches home, and repeated resolution is equal
    - Tag: `// Feature: refactor01-fileio, Property 1: Resolution is absolute, CWD-independent, and rooted at user.home/mclovers`

  - [ ]* 1.4 Write property test for idempotent create and reuse
    - **Property 2: Idempotent create and reuse**
    - **Validates: Requirements 1.6, 2.1, 2.2, 2.3, 3.6**
    - Under a `Files.createTempDirectory` home, prepare base, seed random files, prepare again; assert seeded files unchanged and base still a directory; >= 100 iterations
    - Tag: `// Feature: refactor01-fileio, Property 2: Idempotent create and reuse`

  - [ ]* 1.5 Write property test for separator-free path joining
    - **Property 4: Path joining is separator-free**
    - **Validates: Requirements 6.3**
    - For >= 100 child segment names, assert the join equals `base.resolve(child)` and no hardcoded separator is introduced
    - Tag: `// Feature: refactor01-fileio, Property 4: Path joining is separator-free`

  - [ ]* 1.6 Write property test for paths containing spaces
    - **Property 10: Paths with spaces behave identically to paths without**
    - **Validates: Requirements 8.5**
    - Use temp home dirs containing spaces; assert joins resolve and outcomes match a space-free base; >= 100 iterations
    - Tag: `// Feature: refactor01-fileio, Property 10: Paths with spaces behave identically to paths without`

  - [ ]* 1.7 Write example/error-branch tests for RuntimeDirectory
    - `user.home` null/blank throws `RuntimeDirectoryException` and creates nothing (1.7, 8.1)
    - Base path occupied by a regular file halts without overwrite (2.4, 8.2, 8.4)
    - Use temp directories only; never touch the real user home
    - _Requirements: 1.7, 2.4, 8.1, 8.2, 8.4_

- [x] 2. Migrate ServerLoader to NIO under the base directory
  - [x] 2.1 Convert `ServerLoader` file operations to NIO
    - Modify `app/src/main/java/minecraft/wrapper/ServerLoader.java`, keeping the constructor `ServerLoader(File serverDir, String serverJarName, String eulaFileName)`
    - `ensureDirectoryExists()` uses `Files.createDirectories(serverDir.toPath())`
    - `ensureServerJarExists()` joins via `serverDir.toPath().resolve(serverJarName)`, guards with `Files.exists`, and extracts with `Files.copy(inputStream, path, REPLACE_EXISTING)`; throw `IOException` naming the resource if the classpath jar is missing when extraction is required; preserve an existing jar without overwriting
    - `ensureEulaAccepted()` writes only when absent using `Files.writeString(path, "eula=true")`; preserve an existing EULA
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.10, 6.1, 6.3, 6.4, 7.3_

  - [ ]* 2.2 Write property test for targets rooted under the base
    - **Property 3: All provisioned target paths are absolute and rooted under the base directory**
    - **Validates: Requirements 3.10, 4.1, 4.2, 5.4**
    - For many temp bases, assert every provisioned/resolved path `startsWith(base)` and `isAbsolute()`; >= 100 iterations
    - Tag: `// Feature: refactor01-fileio, Property 3: All provisioned target paths are absolute and rooted under the base directory`

  - [ ]* 2.3 Write property test for preserving existing provisioned files
    - **Property 5: Existing provisioned files are preserved**
    - **Validates: Requirements 3.3, 3.5**
    - Seed jar/EULA with random bytes, run provisioning, assert bytes unchanged; >= 100 iterations
    - Tag: `// Feature: refactor01-fileio, Property 5: Existing provisioned files are preserved`

  - [ ]* 2.4 Write example tests for ServerLoader branches
    - EULA content is exactly `eula=true` when absent (3.4, 7.3)
    - Missing server jar resource throws with the resource name (3.2)
    - _Requirements: 3.2, 3.4, 7.3_

- [x] 3. Migrate PluginsLoader to NIO under the base directory
  - [x] 3.1 Convert `PluginsLoader` file operations to NIO and preserve all-or-nothing behavior
    - Modify `app/src/main/java/minecraft/wrapper/PluginsLoader.java`, keeping the constructor `PluginsLoader(File serverDir)`
    - Create the `plugins` subdirectory via `Files.createDirectories(serverDir.toPath().resolve("plugins"))`; on creation failure throw naming the failed path
    - Preserve the all-or-nothing check: if any of the four plugin resources is missing on the classpath, install none, print the download-link report with each missing plugin name and link, then throw
    - Copy each present plugin with `Files.copy(..., REPLACE_EXISTING)` to a `resolve`-joined target
    - _Requirements: 3.6, 3.7, 3.8, 3.9, 3.10, 6.1, 6.3, 6.4, 7.4_

  - [ ]* 3.2 Write example test for plugins all-or-nothing behavior
    - A simulated missing resource installs none, prints the report, and throws
    - _Requirements: 3.9, 7.4_

- [x] 4. Migrate ServerConfig I/O to NIO streams
  - [x] 4.1 Convert `ServerConfig` load/save to NIO streams
    - Modify `app/src/main/java/minecraft/wrapper/ServerConfig.java`, retaining the constructor `ServerConfig(File file)` so `ServerConfigTest` compiles unchanged
    - `load()` uses `Files.newInputStream(path)` and `save()` uses `Files.newOutputStream(path)`, keeping `java.util.Properties`; `load()` on a missing file starts from empty properties
    - Keep `applyEnvironmentVariables()` semantics unchanged, including forced `enforce-secure-profile=false`
    - _Requirements: 4.1, 4.3, 6.1, 6.4, 7.5, 7.6_

  - [ ]* 4.2 Write property test for server.properties round-trip
    - **Property 6: server.properties round-trip**
    - **Validates: Requirements 4.3**
    - Generate >= 100 random valid key/value maps, save then load, assert equality; use temp files
    - Tag: `// Feature: refactor01-fileio, Property 6: server.properties round-trip`

  - [ ]* 4.3 Write property test for enforced settings winning over environment
    - **Property 7: Enforced settings always win over environment input**
    - **Validates: Requirements 7.5, 7.6**
    - For >= 100 arbitrary env value combinations, apply then enforce, assert `server-port=25565`, `online-mode=true`, `enforce-secure-profile=false`
    - Tag: `// Feature: refactor01-fileio, Property 7: Enforced settings always win over environment input`

- [x] 5. Migrate GeyserConfig path resolution to NIO
  - [x] 5.1 Convert `GeyserConfig` path joining to `Path.resolve` and keep in-place edit
    - Modify `app/src/main/java/minecraft/wrapper/GeyserConfig.java`, keeping the constructor `GeyserConfig(File serverDir)`
    - Build the config path via `serverDir.toPath().resolve("plugins").resolve("Geyser-Spigot").resolve("config.yml")` (no hardcoded separators)
    - `configure()` reads with `Files.readString`, regex-replaces `mtu -> 1200` and `auth-type -> floodgate` independently, warns per absent key, writes with `Files.writeString(..., TRUNCATE_EXISTING)` only when changed, and skips with a message when the file is absent
    - _Requirements: 4.2, 4.4, 4.5, 6.1, 6.3, 6.4_

  - [ ]* 5.2 Write property test for Geyser in-place edit
    - **Property 9: Geyser in-place edit enforces keys and preserves surrounding content**
    - **Validates: Requirements 4.4, 4.5**
    - Generate configs embedding `mtu`/`auth-type` among random lines (and configs missing keys); assert enforced values, surrounding lines intact, absent keys not injected; >= 100 iterations
    - Tag: `// Feature: refactor01-fileio, Property 9: Geyser in-place edit enforces keys and preserves surrounding content`

- [x] 6. Add the pre-launch guard to ServerRunner
  - [x] 6.1 Root ServerRunner working directory at the base and add launch guard
    - Modify `app/src/main/java/minecraft/wrapper/ServerRunner.java`, keeping `getInstance(File workingDir, String jarName)` and `pb.directory(workingDir)` for both `generateConfigs()` and `execute(boolean)` (the value passed is now the resolved base directory)
    - Before launch, if the base directory does not exist or the server jar is absent under it, abort and report an error naming the unresolved working directory or jar
    - Keep `buildJavaCommand()` per-OS launcher resolution unchanged (`bin/java.exe` on Windows, else `bin/java`; `-Xms1024M -Xmx1024M -jar <jarName> [nogui]`)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 9.3_

  - [ ]* 6.2 Write example test for launch guard and launcher resolution
    - Launch guard aborts when base or jar is absent (5.5)
    - Launcher suffix is `bin/java.exe` when `os.name` contains `win`, else `bin/java` (9.3)
    - _Requirements: 5.5, 9.3_

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Wire Phase 0 into App and thread the base directory
  - [x] 8.1 Add Phase 0 resolution and thread the resolved base directory
    - Modify `app/src/main/java/minecraft/wrapper/App.java`: remove `SERVER_DIR_NAME` and `new File("minecraft_server")`
    - Add Phase 0: `Path base = RuntimeDirectory.resolveAndPrepare();` then `File serverDir = base.toFile();`
    - Pass `serverDir` (the resolved base) to `ServerLoader`, `PluginsLoader`, `ServerRunner`, `ServerConfig`, and `GeyserConfig`; keep shadow-run decision, enforced settings (`server-port=25565`, `online-mode=true`), and phase ordering unchanged apart from being rooted under the base
    - Let the existing top-level `catch` handle `RuntimeDirectoryException`, printing a message naming the base directory or missing user home and exiting non-zero
    - _Requirements: 1.5, 3.10, 5.1, 7.1, 7.2, 7.5, 7.6, 7.7, 8.4_

  - [ ]* 8.2 Write property test for the shadow-run decision
    - **Property 8: Shadow-run decision equals not(both configs present)**
    - **Validates: Requirements 7.1, 7.2**
    - Enumerate the four (props, geyser) presence combinations, assert decision equals not(both present); full-space enumeration (4 cases)
    - Tag: `// Feature: refactor01-fileio, Property 8: Shadow-run decision equals not(both configs present)`

  - [ ]* 8.3 Confirm existing tests still pass unchanged
    - Ensure `ServerConfigTest` and `AppTest` pass without modification, confirming backward-compatible constructors
    - _Requirements: 7.5, 7.6_

- [x] 9. Update steering documentation for the new runtime location
  - [x] 9.1 Update `product.md`, `structure.md`, and `tech.md`
    - Update `.kiro/steering/product.md` to describe provisioning at the per-OS Base Runtime Directory `<user.home>/mclovers`, removing any project-relative description
    - Update `.kiro/steering/structure.md` to describe the runtime working directory as `<user.home>/mclovers` rather than a project-area `minecraft_server/`
    - Update `.kiro/steering/tech.md` to describe the child-process working directory as `<user.home>/mclovers` rather than a CWD-relative location
    - _Requirements: 10.1, 10.2, 10.3_

- [x] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP.
- Each task references specific requirements for traceability, and property-test tasks reference the exact correctness property from the design.
- Property tests are implemented as property-style JUnit 4 tests (no new dependency): >= 100 iterations for open input spaces, full enumeration for finite spaces (Property 8). Filesystem tests use `Files.createTempDirectory` and never touch the real user home.
- Checkpoints ensure incremental validation. Run the suite with `./gradlew test`.
- Backward-compatible `File`-accepting constructors are preserved so existing tests continue to pass.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1", "3.1", "4.1", "5.1", "6.1"] },
    {
      "id": 2,
      "tasks": [
        "1.3",
        "1.4",
        "1.5",
        "1.6",
        "1.7",
        "2.2",
        "2.3",
        "2.4",
        "3.2",
        "4.2",
        "4.3",
        "5.2",
        "6.2"
      ]
    },
    { "id": 3, "tasks": ["8.1"] },
    { "id": 4, "tasks": ["8.2", "8.3", "9.1"] }
  ]
}
```
