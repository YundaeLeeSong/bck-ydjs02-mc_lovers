/*
 * Minecraft Wrapper Build Configuration
 *
 * This project uses a standard Gradle Application layout.
 * It produces a native executable using 'jpackage' directly configured here.
 */

plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Unit testing
    testImplementation(libs.junit)
    // Core utilities
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

// --- TASK CONFIGURATIONS ---

// 1. Custom JPackage Task (Distribution)
// Creates a standalone, portable application image for the CURRENT OS.
tasks.register<Exec>("jpackage") {
    // Use installDist to gather all dependencies and the main jar into a single directory
    dependsOn("installDist")

    val jdkHome = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.get().metadata.installationPath

    // [Cross-Platform] Determine executable name
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val jpackageExec = if (isWindows) "jpackage.exe" else "jpackage"
    val jpackageTool = jdkHome.dir("bin").file(jpackageExec).asFile.absolutePath

    // 'installDist' outputs to build/install/<projectName>/lib
    // We point jpackage to this 'lib' folder which contains all jars.
    val inputDir = layout.buildDirectory.dir("install/app/lib").get().asFile.absolutePath
    val outputDir = layout.buildDirectory.dir("dist").get().asFile.absolutePath
    val jarName = "app.jar" // Standard jar name from application plugin
    val appName = "mc-lovers"

    // [Execution] Run the jpackage command
    commandLine(
        jpackageTool,
        "--type", "app-image",          // Create directory structure (portable)
        "--dest", outputDir,
        "--input", inputDir,
        "--main-jar", jarName,
        "--main-class", "minecraft.wrapper.App",
        "--name", appName,
        "--java-options", "-Xmx64m"     // Low memory overhead for the wrapper itself
    )

    if (isWindows) {
        args("--win-console")           // Windows only: Keep console open
    }

    // [Pre-Clean] specific to this task
    doFirst {
        val distFolder = file(outputDir)
        if (distFolder.exists()) {
            distFolder.deleteRecursively()
        }
        println("Using jpackage from: $jpackageTool")
        println("Input Directory: $inputDir")
    }

    // [Post-Processing Fix]
    // jpackage creates a stripped-down runtime that usually excludes 'bin/java.exe'
    // because the native launcher uses the JVM DLL directly.
    // However, our wrapper needs to spawn a *new* Java process for the Minecraft server.
    // Therefore, we must manually copy the 'java' executable from the JDK into the distribution.
    doLast {
        val javaName = if (isWindows) "java.exe" else "java"
        val sourceJava = jdkHome.dir("bin").file(javaName).asFile
        
        // Determine correct runtime location based on OS layout
        // Windows: <app>/runtime
        // Linux/Mac: <app>/lib/runtime
        val runtimePath = if (isWindows) "runtime" else "lib/runtime"
        val destJava = file(outputDir).resolve("$appName/$runtimePath/bin/$javaName")

        println(">>> Patching Runtime for Child Process Support")
        
        if (sourceJava.exists()) {
            // Ensure destination directory exists (especially 'bin' if it was stripped)
            destJava.parentFile.mkdirs()
            
            sourceJava.copyTo(destJava, overwrite = true)
            println("Success: Copied $javaName to ${destJava.absolutePath}")
            
            // Set executable permission on Linux/Mac
            if (!isWindows) {
                try {
                    val chmod = ProcessBuilder("chmod", "+x", destJava.absolutePath).start()
                    chmod.waitFor()
                    println("Success: Set executable permission on $javaName")
                } catch (e: Exception) {
                    println("WARNING: Failed to set executable permission: ${e.message}")
                }
            }
        } else {
            println("WARNING: Failed to patch runtime. Child processes might fail.")
            println("Source: $sourceJava")
            println("Dest: $destJava")
        }
        
        println("\n========================================================")
        println(" Distribution Created Successfully!")
        println(" Location: ${file(outputDir).resolve(appName)}")
        println("========================================================")
    }
}

// --- Robust Clean Task ---
// Windows often locks the 'dist' folder because the executable might be in use (zombie process).
// This configuration extends the standard 'clean' task to handle these errors gracefully.
tasks.named("clean") {
    doFirst {
        // 1. Clean 'dist' directory (JPackage output)
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        if (distDir.exists()) {
            println("Cleaning distribution directory: $distDir")
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            try {
                if (isWindows) {
                    // Try forced delete on Windows
                    exec {
                        commandLine("cmd", "/c", "rmdir", "/s", "/q", distDir.absolutePath)
                        isIgnoreExitValue = true
                    }
                }
                if (distDir.exists() && !distDir.deleteRecursively()) {
                     println("WARNING: Failed to fully delete 'dist'. Files might be locked.")
                }
            } catch (e: Exception) {
                 println("WARNING: Error cleaning 'dist': ${e.message}")
            }
        }

        // 2. Clean 'bin' directory (IDE output)
        val binDir = project.file("bin")
        if (binDir.exists()) {
             println("Cleaning binary directory: $binDir")
             binDir.deleteRecursively()
        }
        
        // 3. Clean Runtime data directories
        listOf("minecraft_server", "velocity_proxy").forEach { dirName ->
            val dir = project.file(dirName)
            if (dir.exists()) {
                println("Cleaning runtime directory: $dir")
                dir.deleteRecursively()
            }
        }
    }
}