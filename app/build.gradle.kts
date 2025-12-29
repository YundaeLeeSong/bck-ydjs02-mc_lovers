/*
 * Minecraft Wrapper Build Configuration
 *
 * This project uses a standard Gradle Application layout.
 * It produces a native executable using 'jpackage' via the imported script.
 */

plugins {
    // Provides 'installDist' task and standard Java application directory layout
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Unit testing
    testImplementation(libs.junit)
    // Core utilities (used for IO operations etc)
    implementation(libs.guava)
}

// Java 21 is required for modern Minecraft Server versions (1.20.5+)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    // The entry point class for the wrapper
    mainClass.set("minecraft.wrapper.App")
}

// Import the custom JPackage logic from the separate script file
// This keeps this main build file clean and focused on dependencies/config
apply(from = "../gradle/jpackage.gradle.kts")

// --- Robust Clean Task ---
// Windows often locks the 'dist' folder because the executable might be in use (zombie process).
// This configuration extends the standard 'clean' task to handle these errors gracefully
// by warning the user instead of crashing with a stack trace.
tasks.named("clean") {
    doFirst {
        // 1. Clean 'dist' directory (JPackage output)
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        if (distDir.exists()) {
            println("Cleaning distribution directory: $distDir")
            try {
                if (!distDir.deleteRecursively()) {
                    println("WARNING: Failed to fully delete 'dist'. Files might be locked by a running instance.")
                    println("Tip: Check Task Manager for 'MinecraftWrapper.exe' or 'java.exe'.")
                }
            } catch (e: Exception) {
                 println("WARNING: Error cleaning 'dist': ${e.message}")
            }
        }

        // 2. Clean 'bin' directory (IDE output, e.g., VSCode/Eclipse)
        // Note: We use project.file("bin") because it sits in 'app/bin', not 'app/build/bin'.
        val binDir = project.file("bin")
        if (binDir.exists()) {
            println("Cleaning binary directory: $binDir")
            try {
                if (!binDir.deleteRecursively()) {
                    println("WARNING: Failed to fully delete 'bin'. Files might be locked by a running instance.")
                    println("Tip: Check Task Manager for 'MinecraftWrapper.exe' or 'java.exe'.")
                }
            }
            catch (e: Exception) {
                println("WARNING: Error cleaning 'bin': ${e.message}")
            }
        }
    }
}
