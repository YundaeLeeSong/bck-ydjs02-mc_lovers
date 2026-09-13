# Implementation Plan: refactor02-dynamic_resource_alloc

## Overview

This plan converts the dynamic-provisioning design into incremental Java 21 coding steps. It builds the foundation first (exception type, config keys, config loader, recognition types), then the pure recognition layer, the download seam, the provisioner orchestration, and finally wires everything into `App` while narrowing `ServerLoader`, retiring `PluginsLoader`, and feeding the recognized server jar name to `ServerRunner`. Each correctness property (1-20) from the design is implemented as a property-style JUnit 4 test placed close to the code it validates. The resource jars are removed and replaced by a single bundled `download.properties`, and the steering docs are updated last.

All new source lives under `app/src/main/java/minecraft/wrapper/`, tests under `app/src/test/java/minecraft/wrapper/`, using JUnit 4 only (no new dependency; Guava + JUnit 4 remain the only catalog entries). Tests use `Files.createTempDirectory` for staging/base and a JDK `com.sun.net.httpserver.HttpServer` (or a fake `Downloader`) for HTTP, never touching the real user home or the network.

## Tasks

- [x] 1. Add the ProvisioningException type
  - [x] 1.1 Create `ProvisioningException`
    - Add `app/src/main/java/minecraft/wrapper/ProvisioningException.java`, a checked exception mirroring `RuntimeDirectoryException`: carries an optional offending `Path` and the underlying cause, with constructors accepting (message), (message, cause), and (message, path, cause), and an accessor for the path.
    - Target Java 21.
    - _Requirements: 6.1, 9.7_

  - [ ]* 1.2 Write unit test for ProvisioningException
    - Assert message, path accessor, and cause are preserved through each constructor.
    - _Requirements: 6.1_

- [x] 2. Define recognition and plugin-identity types
  - [x] 2.1 Create `PluginId` enum
    - Add `PluginId.java` with values `GEYSER, FLOODGATE, VIAVERSION, VIABACKWARDS`.
    - _Requirements: 3.2, 5.1_

  - [x] 2.2 Create `RecognitionPatterns` and `RecognitionResult` records
    - Add `RecognitionPatterns` (server `Pattern` + `Map<PluginId, Pattern>`) and `RecognitionResult` (chosen server `Path` + `Map<PluginId, Path>`) as records in the `minecraft.wrapper` package.
    - _Requirements: 3.1, 3.2, 4.5_

- [x] 3. Implement the Download Config loader
  - [x] 3.1 Create `DownloadConfig`
    - Add `DownloadConfig.java`. Static `load(String resourceName)` reads via `App.class.getResourceAsStream("/" + resourceName)` and parses with `java.util.Properties`; a null stream aborts (missing resource), a parse failure aborts (malformed).
    - Validate every required key present and non-blank, including the two directory-name keys `base.dir.name` and `staging.dir.name`; on failure throw `ProvisioningException` naming the offending key. Directory-name keys are validated so `App` can abort before Phase 0.
    - Accessors: `baseDirName()` (`base.dir.name`, documented default `mclovers`), `stagingDirName()` (`staging.dir.name`, documented default `resources`), `serverUrl()`, `pluginUrl(PluginId)`, `serverPattern()`, `pluginPattern(PluginId)`. No `.target` keys.
    - Parse patterns via `java.util.regex.Pattern.compile`.
    - _Requirements: 5.1, 5.4, 5.6, 5.7, 5.8, 5.9, 5.11, 9.3, 9.7_

  - [ ]* 3.2 Write property test for missing/blank required config key aborting before download
    - **Property 11: A missing or blank required config key aborts before any download**
    - **Validates: Requirements 5.6, 5.7**
    - For each required URL/pattern key, remove or blank it; assert `load` (or provisioning wiring) aborts naming the key and a spy `Downloader` is never called. >= 100 iterations over the key set.

  - [ ]* 3.3 Write property test for directory-name keys aborting before resolution
    - **Property 20: A missing or blank directory-name key aborts before any directory is resolved or created**
    - **Validates: Requirements 5.10**
    - For each of `base.dir.name` and `staging.dir.name`, remove or blank it; assert startup aborts naming the offending key before any directory is resolved or created. Enumerate the two keys plus blank/whitespace variants.

  - [ ]* 3.4 Write unit tests for config resource-absent and malformed
    - Config resource absent on classpath aborts as missing before any download; a malformed properties stream aborts as malformed.
    - _Requirements: 5.5, 9.7_

- [x] 4. Modify RuntimeDirectory with additive, backward-compatible overloads
  - [x] 4.1 Add name-parameterized overloads to `RuntimeDirectory`
    - Add `public static Path resolveAndPrepare(String dirName)` and `static Path resolve(String userHome, String dirName)`; the retained no-arg `resolveAndPrepare()` and single-arg `resolve(String)` delegate with `DIR_NAME = "mclovers"`.
    - New `resolve(userHome, dirName)` keeps the null/blank `userHome` check, additionally rejects null/blank `dirName`, and returns `Paths.get(userHome).resolve(dirName).toAbsolutePath().normalize()` (no hardcoded separator). New `resolveAndPrepare(dirName)` reads `user.home`, delegates to the new `resolve`, then applies the identical create -> is-directory -> writable validation order.
    - _Requirements: 5.8, 5.9, 10.4, 10.5_

  - [ ]* 4.2 Write unit test for RuntimeDirectory overloads and backward compatibility
    - `resolve(userHome, dirName)` resolves `<home>/<dirName>` and rejects null/blank `dirName`; the retained no-arg overloads still resolve `<home>/mclovers`. Confirm refactor01's existing `RuntimeDirectory`/`AppTest`/`ServerConfigTest` behaviors are unaffected.
    - _Requirements: 5.8, 5.9_

- [x] 5. Implement the pure JarRecognizer
  - [x] 5.1 Create `JarRecognizer`
    - Add `JarRecognizer.java` constructed with `RecognitionPatterns`; `recognize(List<Path> stagedFiles)` classifies each staged filename by regex, applies server-over-plugin precedence (a file matching both is the server jar only), and applies a case-sensitive ASCII-lexicographic ascending tie-break when multiple files match one artifact, recording the chosen file.
    - Abort with `ProvisioningException` naming the artifact when the server pattern matches nothing or any one of the four plugin patterns matches nothing; leave staged files unmodified. No I/O beyond the supplied list.
    - Return a `RecognitionResult`.
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 6.3_

  - [ ]* 5.2 Write property test for recognition correctness, precedence, and tie-break
    - **Property 9: Recognition selects the correct distinct artifact with precedence and deterministic tie-break**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.6**
    - Generate staged filename sets (including ambiguous server/plugin matches and multi-match cases); assert distinct correct mapping, server precedence, and ASCII-min choice recorded. >= 100 iterations.

  - [ ]* 5.3 Write unit tests for no-match abort branches
    - No server match aborts naming the server jar and names staging; a missing plugin match aborts naming that plugin; staged files left unmodified.
    - _Requirements: 3.4, 3.5_

- [x] 6. Implement the Downloader seam and HttpDownloader
  - [x] 6.1 Create the `Downloader` interface
    - Add `Downloader.java` with `Path download(String url, Path stagingDir) throws ProvisioningException`, documented to stage under a temp name, verify complete + non-empty, atomically move to the `Original_Filename` derived from the response, and return the staged `Path`.
    - _Requirements: 2.6, 9.1_

  - [x] 6.2 Implement `HttpDownloader`
    - Add `HttpDownloader.java` wrapping `HttpClient.newBuilder().followRedirects(Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()` with a 30s per-request timeout and a redirect cap of 5. Stream `BodyHandlers.ofInputStream()` to `<name>.part` in staging.
    - Derive `Original_Filename` from the `Content-Disposition` `filename` parameter, else the final `HttpResponse.uri()` last path segment. On success move `.part` to the resolved filename via `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` with a plain `REPLACE_EXISTING` fallback.
    - Verify non-empty and complete transfer; on zero-byte, truncated, non-2xx, or timeout, delete the partial/empty file (confirm absent) and throw `ProvisioningException` naming the artifact/URL/status; retry up to 3 attempts per artifact with no fallback to any prior/bundled jar.
    - Use only `java.net.http` and `java.nio.file`.
    - _Requirements: 2.1, 2.2, 2.4, 2.6, 2.7, 2.8, 6.1, 6.2, 6.6, 7.1, 7.2, 7.3, 7.4, 7.5, 9.1, 9.6_

  - [ ]* 6.3 Write property test for download + non-empty verification preserving bytes
    - **Property 4: Download then non-empty verification preserves bytes**
    - **Validates: Requirements 2.1, 2.2, 2.6, 7.1, 7.6**
    - Local `HttpServer` serves random non-empty bodies per artifact; assert staged bytes equal served bytes and size > 0; only verified files are available. >= 100 iterations.

  - [ ]* 6.4 Write property test for non-success HTTP status aborting with nothing injected
    - **Property 6: Non-success HTTP status aborts with nothing injected**
    - **Validates: Requirements 2.8, 9.6**
    - Local server returns generated non-2xx codes; assert abort names artifact/URL/status and base has no injected jar. >= 100 iterations.

  - [ ]* 6.5 Write property test for interrupted download leaving no recognized artifact
    - **Property 7: An interrupted download leaves no recognized artifact**
    - **Validates: Requirements 6.6, 7.3**
    - Fake/local server writes a part then interrupts (Content-Length mismatch); assert no final-name file, part cleaned, recognition finds nothing. >= 100 iterations.

  - [ ]* 6.6 Write property test for zero-byte download rejected and cleaned
    - **Property 8: Zero-byte download is rejected and cleaned**
    - **Validates: Requirements 7.2, 7.4**
    - Serve an empty body for a random artifact; assert the empty file is deleted (confirmed absent) and provisioning aborts naming the artifact. >= 100 iterations.

  - [ ]* 6.7 Write unit tests for download error/edge branches
    - Redirect chain up to 5 hops resolves to the final body (2.4); download timeout via stalling server or fake aborts naming the artifact with base unchanged (2.7, 6.1); retry attempted at most 3 times then aborts with no fallback (6.2); truncated `Content-Length` mismatch treated as failed, partial deleted, aborts (7.3); deletion-of-partial failure still aborts reporting both failures (7.5); `Content-Disposition` filename honored over URI segment.
    - _Requirements: 2.4, 2.7, 6.1, 6.2, 7.3, 7.5_

- [x] 7. Implement the ResourceProvisioner orchestration
  - [x] 7.1 Implement staging resolution and wipe/recreate
    - Add `ResourceProvisioner.java` with constructor `(Path baseDir, DownloadConfig config, Downloader downloader)` and NO `STAGING_DIR_NAME` constant (staging name comes from `config.stagingDirName()`).
    - `static Path resolveStaging(String userHome, String stagingDirName)` is side-effect-free, rejects null/blank home, and returns `Paths.get(userHome).resolve(stagingDirName).toAbsolutePath().normalize()`.
    - `Path wipeAndRecreateStaging()` recursively deletes depth-first via NIO (missing dir is a no-op), recreates with `Files.createDirectories`, guarded by a 30s deadline; a locked/in-use entry aborts naming the path and cause, and a timeout aborts naming the path and timeout.
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6, 1.7, 5.9, 9.2, 10.4, 10.5_

  - [ ]* 7.2 Write property test for staging resolution being absolute, CWD-independent, separator-free, config-named
    - **Property 1: Staging resolution is absolute, CWD-independent, separator-free, and named by config**
    - **Validates: Requirements 1.1, 5.9, 10.4**
    - Generate home strings and staging names (including spaces, nested); assert final segment equals configured name, absolute, parent matches home, equals `resolveStaging(home, name)`, CWD-independent. >= 100 iterations.

  - [ ]* 7.3 Write property test for unresolvable user home aborting with base unchanged
    - **Property 2: Unresolvable user home aborts and leaves the base unchanged**
    - **Validates: Requirements 10.5**
    - null/blank/whitespace homes; assert throws naming home, no filesystem access, base contents unchanged. >= 100 iterations.

  - [ ]* 7.4 Write property test for wipe/recreate leaving an empty staging directory
    - **Property 3: Wipe and recreate leaves an empty staging directory**
    - **Validates: Requirements 1.2, 1.3, 1.4**
    - Seed random trees (including the absent case) under a temp staging dir; wipe+recreate; assert the directory exists and contains zero entries. >= 100 iterations.

  - [ ]* 7.5 Write unit tests for wipe locked-file and timeout branches
    - Locked/read-only entry aborts naming the path and cause with no download attempted (1.6) where the OS permits forcing it; wipe timeout via an injected deadline seam aborts naming the path (1.7).
    - _Requirements: 1.6, 1.7_

  - [x] 7.6 Implement download-recognize-verify-inject pipeline in `provision()`
    - `provision()` resolves staging under `config.stagingDirName()`, calls `wipeAndRecreateStaging()`, downloads the server jar and four plugin jars via the injected `Downloader` using URLs from `config`, recognizes via `JarRecognizer`, re-verifies each recognized file non-empty immediately before copy, then injects: copy the server jar to `base/<originalServerFilename>` and each plugin to `base/plugins/<originalPluginFilename>` using `source.getFileName().toString()` and `Files.copy(REPLACE_EXISTING)`, creating `base/plugins` if absent.
    - Return the injected server jar's `Original_Filename`.
    - On any failure clean partial/unrecognized artifacts from both staging and base, preserve pre-existing base contents, launch no child process, and throw `ProvisioningException` (missing source names it, pre-injection empty names the artifact, copy failure reports source and destination).
    - _Requirements: 2.5, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 6.4, 6.5, 7.6, 7.7, 10.6_

  - [ ]* 7.7 Write property test for requested URL equalling configured URL
    - **Property 5: Requested URL equals the configured URL**
    - **Validates: Requirements 2.5, 5.1, 5.4**
    - Generate configs with random URLs; spy `Downloader`; assert the requested URL per artifact equals the config value, never a compiled literal. >= 100 iterations.

  - [ ]* 7.8 Write property test for injection under Original_Filename, rooted absolutely, overwriting
    - **Property 10: Injection places jars under their Original_Filename, rooted absolutely under the base, overwriting**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.5**
    - For many temp bases (some with existing same-name targets) and generated versioned source filenames, inject; assert destinations exist with `getFileName()` equal to source filename, bytes equal source, absolute, startsWith base, and pre-existing same-name overwritten. >= 100 iterations.

  - [ ]* 7.9 Write property test for server jar name returned equalling recognized Original_Filename
    - **Property 18: The server jar name handed to ServerRunner equals the recognized Original_Filename**
    - **Validates: Requirements 4.5, 8.13**
    - Generate recognized staging sets with varying versioned server filenames; assert `provision()` returns exactly `recognizedServerJar.getFileName().toString()`. >= 100 iterations.

  - [ ]* 7.10 Write property test for fail-fast leaving nothing stale and base preserved
    - **Property 12: On any provisioning failure nothing stale or recognizable remains and the base is preserved**
    - **Validates: Requirements 6.4, 7.4, 10.6**
    - Force failure at each step (wipe, download, recognition, verification, injection); assert no recognizable/partial artifact remains in staging or base, pre-seeded base contents unchanged, no launch. >= 100 iterations over failure steps.

  - [ ]* 7.11 Write property test for paths-with-spaces parity
    - **Property 13: Paths with spaces behave identically to paths without**
    - **Validates: Requirements 10.3**
    - Temp homes/bases containing spaces; assert resolve/wipe/download/recognize/inject reach identical outcomes to space-free paths. >= 100 iterations.

  - [ ]* 7.12 Write unit tests for injection source-absent and copy-fail branches
    - Recognized source absent at injection aborts naming the missing source (by `Original_Filename`) with no launch (4.6); copy into a read-only base aborts naming source/dest with no launch, where the OS supports it (4.7).
    - _Requirements: 4.6, 4.7_

- [x] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Narrow ServerLoader and retire PluginsLoader
  - [x] 9.1 Narrow `ServerLoader`
    - Remove `ensureServerJarExists()` and the server jar name argument; `install()` reduces to `ensureDirectoryExists()` + `ensureEulaAccepted()`, writing `eula=true` via `Files.writeString` only when `eula.txt` is absent and preserving an existing file.
    - _Requirements: 8.1, 8.2_

  - [ ]* 9.2 Write property test for EULA preserved / created
    - **Property 14: Existing EULA is preserved**
    - **Validates: Requirements 8.1, 8.2**
    - Seed random `eula.txt` content and assert byte-for-byte unchanged; absent case asserts the file created containing exactly `eula=true`. >= 100 iterations.

  - [x] 9.3 Retire `PluginsLoader` classpath-copy
    - Remove `PluginsLoader`'s classpath-copy responsibility and its all-or-nothing classpath report (reduce to a no-op or remove the class); the all-four-present guarantee now lives in `JarRecognizer`. Remove its construction/`install()` call site preparation for App wiring.
    - _Requirements: 3.5, 4.2_

- [x] 10. Wire provisioning into App
  - [x] 10.1 Load config first and thread provisioning into `App.main`
    - Load `DownloadConfig cfg = DownloadConfig.load("download.properties")` before Phase 0; resolve `Path base = RuntimeDirectory.resolveAndPrepare(cfg.baseDirName())`; construct `new ResourceProvisioner(base, cfg, new HttpDownloader())`; `String serverJarName = provisioner.provision()`.
    - Remove the `SERVER_JAR_NAME` constant and any `"mclovers"`/`"resources"`/`"server.jar"` literals; still construct `ServerLoader` for directory + EULA; remove `PluginsLoader` construction/`install()`.
    - Pass `serverJarName` to `ServerRunner.getInstance(serverDir, serverJarName)` so `buildJavaCommand` emits `-jar <serverJarName>` and `ensureLaunchReady` resolves `base.resolve(serverJarName)` for both shadow and blocking runs. Keep shadow-run decision, enforced Bedrock settings, and network report unchanged.
    - Add a top-level `catch (ProvisioningException e)` that prints the actionable message and `System.exit(1)`, placed before the `RuntimeDirectoryException` handler.
    - _Requirements: 2.3, 4.4, 4.5, 5.5, 5.8, 5.9, 5.10, 5.11, 6.5, 8.3, 8.4, 8.9, 8.13_

  - [ ]* 10.2 Write property test for both directory names coming from config
    - **Property 19: Both directory names come from config, with no hardcoded directory-name literal**
    - **Validates: Requirements 1.1, 5.8, 5.9, 5.11**
    - Generate configs with random non-blank base/staging names; resolve base via `RuntimeDirectory.resolve(home, cfg.baseDirName())` and staging via `resolveStaging(home, cfg.stagingDirName())`; assert each final segment equals the configured name and tracks config, not a compiled literal. >= 100 iterations.

  - [ ]* 10.3 Write property test for shadow-run decision
    - **Property 15: Shadow-run decision equals not(both configs present)**
    - **Validates: Requirements 8.3, 8.4**
    - Enumerate the four presence combinations of `server.properties` and Geyser `config.yml`; assert the Shadow Run runs exactly when at least one is absent. Full enumeration (4 cases).

  - [ ]* 10.4 Write integration tests for order-of-operations and ServerRunner launch-name wiring
    - Spy on call order: `DownloadConfig.load` first, then `RuntimeDirectory.resolveAndPrepare(cfg.baseDirName())`, then `ResourceProvisioner.provision()` completes before the Shadow-Run decision and blocking run; a forced config failure prevents base resolution and a forced provisioning failure runs neither run. Assert the `jarName` handed to `ServerRunner.getInstance` equals the recognized server jar `Original_Filename` and the emitted command is `-jar <originalServerFilename>` for both runs. Single smoke assertion that `NetworkReporter.printReport()` prints in the execute phase.
    - _Requirements: 1.5, 2.3, 4.4, 4.5, 5.10, 6.5, 8.9, 8.10, 8.11, 8.12, 8.13_

- [ ] 11. Preserve ServerConfig and GeyserConfig behavior under provisioning
  - [ ]* 11.1 Write property test for enforced Bedrock settings winning over environment
    - **Property 16: Enforced Bedrock settings always win over environment input**
    - **Validates: Requirements 8.5, 8.6**
    - Random env values; apply + enforce; assert `server-port=25565`, `online-mode=true`, `enforce-secure-profile=false`. >= 100 iterations.

  - [ ]* 11.2 Write property test for Geyser in-place edit
    - **Property 17: Geyser in-place edit enforces keys and preserves surrounding content**
    - **Validates: Requirements 8.7, 8.8**
    - Configs embedding `mtu`/`auth-type` among random lines (and configs missing keys); assert enforced values `mtu=1200`/`auth-type=floodgate`, other lines intact, absent keys neither created nor corrupted. >= 100 iterations.

- [x] 12. Replace bundled resources with the Download Config
  - [x] 12.1 Create `download.properties`
    - Add `app/src/main/resources/download.properties` with `base.dir.name=mclovers`, `staging.dir.name=resources`, and the server + four plugin `url`/`pattern` keys (Purpur latest server URL, GeyserMC Geyser and Floodgate spigot URLs, Hangar ViaVersion/ViaBackwards URLs; patterns such as `^purpur-.*\.jar$`, `^Geyser-Spigot.*\.jar$`, `^floodgate-spigot.*\.jar$`, `^ViaVersion.*\.jar$`, `^ViaBackwards.*\.jar$`). No `.target` keys.
    - _Requirements: 5.1, 5.8, 5.11, 2.5_

  - [x] 12.2 Delete bundled jars from resources
    - Delete `app/src/main/resources/server.jar`, `server-mojang.jar`, `server-paper.jar`, `server-purpur.jar`, and `app/src/main/resources/plugins/Geyser-Spigot.jar`, `floodgate-spigot.jar`, `ViaVersion.jar`, `ViaBackwards.jar` so resources carry no jar.
    - _Requirements: 5.2, 5.3_

  - [ ]* 12.3 Write build/config smoke checks
    - Assert `app/src/main/resources` contains no `server*.jar` and `resources/plugins` contains no `*.jar` (5.2, 5.3); `download.properties` carries `base.dir.name`/`staging.dir.name` with defaults `mclovers`/`resources`; `App`/`ResourceProvisioner` hold no hardcoded `"mclovers"`/`"resources"`/`"server.jar"` directory-name literal (5.8, 5.11); the catalog declares only Guava and JUnit (9.4); toolchain is Java 21 (9.5); provisioning uses `java.net.http`, NIO, and `java.util.Properties` (9.1, 9.2, 9.3).
    - _Requirements: 5.2, 5.3, 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 13. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Update steering documentation for dynamic provisioning
  - [x] 14.1 Update `product.md`, `structure.md`, `tech.md`
    - `product.md`: describe server and plugin jars as downloaded fresh into the Staging Directory (default `<user.home>/resources`, name from Download Config) each run and injected into the Base Runtime Directory (default `<user.home>/mclovers`, name from Download Config); remove any statement that the bundle carries jars as classpath resources.
    - `structure.md`: describe `app/src/main/resources` as holding only the `download.properties` Download Config (directory names, URLs, patterns); remove references to server jars at the resources root and plugin jars under `resources/plugins`.
    - `tech.md`: describe server and plugins as obtained over the internet each run rather than shipped as classpath resources, and describe the fail-fast behavior when provisioning cannot complete (unreachable source or absent downloaded artifact).
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

- [x] 15. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test tasks and can be skipped for a faster MVP; core implementation and resource-file tasks are never optional.
- Each task references specific requirements for traceability; each property test references its design property number and validated requirements.
- Tests use JUnit 4 only (no new dependency); property-style tests run >= 100 iterations over open input spaces and enumerate finite spaces (Property 15 = 4 cases, Property 20 = 2 keys).
- `resolveStaging(userHome, name)` and `RuntimeDirectory.resolve(userHome, dirName)` take explicit params so tests use `Files.createTempDirectory` and never touch the real user home; HTTP is exercised via a JDK `com.sun.net.httpserver.HttpServer` or a fake `Downloader`.
- The `RuntimeDirectory` change is additive and backward compatible, so refactor01's existing tests continue to pass.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "4.1", "12.1", "12.2"] },
    { "id": 1, "tasks": ["1.2", "2.2", "3.1", "4.2"] },
    { "id": 2, "tasks": ["3.2", "3.3", "3.4", "5.1", "6.1"] },
    { "id": 3, "tasks": ["5.2", "5.3", "6.2", "9.1", "9.3"] },
    { "id": 4, "tasks": ["6.3", "6.4", "6.5", "6.6", "6.7", "7.1", "9.2"] },
    { "id": 5, "tasks": ["7.2", "7.3", "7.4", "7.5", "7.6"] },
    { "id": 6, "tasks": ["7.7", "7.8", "7.9", "7.10", "7.11", "7.12", "10.1"] },
    {
      "id": 7,
      "tasks": ["10.2", "10.3", "10.4", "11.1", "11.2", "12.3", "14.1"]
    }
  ]
}
```
