# Running the build in IntelliJ IDEA (Gradle fix)

This note explains a build failure seen when opening the project in IntelliJ IDEA, what caused it, and exactly what was changed to fix it. Read it if Gradle sync fails with a `WindowsRegistry is not supported on this operating system` or `SystemInfo is not supported on this operating system` error.

## Symptom

Opening the project in IntelliJ (or running `gradlew`) failed during Gradle sync, before any code was compiled, with:

```text
Failed to calculate the value of task ':app:compileJava' property 'javaCompiler'.
WindowsRegistry is not supported on this operating system.
```

IntelliJ also reported `Project source sets cannot be resolved`, so the whole project failed to import.

## Root cause

The machine is **Windows on ARM64** (an ARM-based Surface). The project's Gradle wrapper was pinned to **Gradle 8.5**, and that version does not ship the native platform integration for Windows/ARM64.

The trigger is the Java **toolchain** declared in `app/build.gradle.kts`:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

When a toolchain is declared, Gradle must resolve a matching JDK. On Windows it does this by enumerating installed JDKs, which calls native OS services (`WindowsRegistry` to read the registry, `SystemInfo` for platform details). On Windows/ARM64 under Gradle 8.5 those native services are unavailable, so the call throws and the build dies while computing the `javaCompiler` property, that is, before compilation.

This is an environment/tooling mismatch, not a bug in the wrapper source code. The Java code compiled fine all along when Gradle was pointed at a JDK directly.

Gradle added official support for running on Windows ARM (ARM64) in **Gradle 9.2.0**. See the [Gradle 9.2.0 release notes](https://docs.gradle.org/9.2.0/release-notes.html).

## The fix

Three changes were made. The wrapper upgrade is the real fix; the other two support it.

### 1. Upgrade the Gradle wrapper from 8.5 to 9.2.0 (the fix)

File: `gradle/wrapper/gradle-wrapper.properties`

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.2.0-bin.zip
```

Gradle 9.2.0 runs natively on Windows/ARM64, so the toolchain resolution no longer calls a missing native service. This was applied with the wrapper task (run once with a Java 21 JDK on the path):

```bash
./gradlew wrapper --gradle-version 9.2.0
```

### 2. Fix a Gradle 8 to 9 breaking change in the build script

File: `app/build.gradle.kts`, in the custom `clean` task.

Gradle 9 removed the `Project.exec {}` method from the task-configuration context, so the old Windows-only force-delete no longer compiled:

```kotlin
// Removed (no longer valid under Gradle 9):
if (isWindows) {
    exec {
        commandLine("cmd", "/c", "rmdir", "/s", "/q", distDir.absolutePath)
        isIgnoreExitValue = true
    }
}
```

That block was redundant: the line right after it already deletes the tree with `distDir.deleteRecursively()`, which is pure JVM file I/O and works on every OS. The `exec { rmdir }` block was deleted and the recursive delete kept, so `clean` behaves the same (including the locked-file warning) without the removed API.

### 3. Disable Gradle toolchain auto-detection

File: `gradle.properties` (new, at the project root)

```properties
org.gradle.java.installations.auto-detect=false
org.gradle.java.installations.auto-download=false
```

With auto-detect off, Gradle does not enumerate installed JDKs at all. It satisfies the Java 21 toolchain from the JDK it is already running on (the "Gradle JVM"). This keeps builds deterministic and avoids the JDK-discovery path that caused the original failure.

Because auto-detection is off, the Gradle JVM itself must be a Java 21 JDK. That is configured in IntelliJ (next section) and, on the command line, by launching `gradlew` with a Java 21 JDK on `JAVA_HOME`/`PATH`.

## IntelliJ IDEA setup

After pulling these changes, configure IntelliJ so its Gradle runs on Java 21.

1. Let IntelliJ re-sync. If it cached the old wrapper, use **File -> Invalidate Caches / Restart**, or re-import the project.
2. **Settings -> Build, Execution, Deployment -> Build Tools -> Gradle**
   - Set **Gradle JVM** to a Java 21 JDK (for example `C:\Users\<you>\.jdks\ms-21.0.11`).
   - Optionally set **Build and run using** and **Run tests using** to **IntelliJ IDEA** to skip the Gradle task graph for everyday compile/run.
3. **File -> Project Structure -> Project**
   - Set **SDK** to the same Java 21 JDK.
4. Re-run the Gradle sync. It should now succeed.

## Verifying from the command line

Run with a Java 21 JDK. On a shell where `JAVA_HOME` is not already Java 21:

```bash
JAVA_HOME="C:/Users/<you>/.jdks/ms-21.0.11" \
PATH="C:/Users/<you>/.jdks/ms-21.0.11/bin:$PATH" \
./gradlew clean test
```

Expected result: `BUILD SUCCESSFUL`, the existing tests pass, and no `WindowsRegistry` / `SystemInfo` errors appear.

## Notes

- A build warning may appear: "Deprecated Gradle features were used ... incompatible with Gradle 10." It originates from the `application` plugin internals in Gradle 9.2.0, not from this project's build script, and is safe to leave for now. Run `./gradlew help --warning-mode all` to inspect warnings.
- If you are on Windows x64 (not ARM64), Gradle 8.5 would not have hit this specific failure, but the upgrade to 9.2.0 is still valid and the setup above still applies.
