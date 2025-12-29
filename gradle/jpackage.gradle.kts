import org.gradle.jvm.toolchain.JavaToolchainService

// ============================================================================ 
// JPackage Distribution Task
// ============================================================================ 
// This script configures the 'createDist' task, which creates a native
// self-contained executable for the application.
//
// Workflow:
// 1. Depends on 'installDist' to compile code and gather dependencies.
// 2. Uses 'jpackage' to build a platform-specific application image.
// 3. Post-processes the image to include 'java.exe' in the runtime, 
//    enabling the wrapper to spawn child Java processes (the Minecraft server).
// ============================================================================ 

val createDist by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Bundles the app using jpackage (via installDist) and fixes the missing java.exe"
    
    // We use installDist as input because it creates a flat 'lib' directory with all jars.
    // This is more reliable for jpackage than a single fat jar, minimizing resource exclusion issues.
    dependsOn("installDist")

    val buildDir = layout.buildDirectory.get().asFile
    val inputDir = buildDir.resolve("install/app/lib")
    val outputDir = buildDir.resolve("dist")
    val appName = "MinecraftWrapper"

    // --- Toolchain Detection ---
    // We dynamically resolve the path to the JDK defined in the main build file.
    // This ensures we use the correct 'jpackage' and 'java' executable matching the project target (Java 21).
    val toolchainService = project.extensions.getByType(JavaToolchainService::class.java)
    val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
    val toolchain = toolchainService.launcherFor(javaExtension.toolchain).get()
    val jdkHome = toolchain.metadata.installationPath.asFile
    
    // Detect OS to set correct executable names
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val jpackageName = if (isWindows) "jpackage.exe" else "jpackage"
    val javaName = if (isWindows) "java.exe" else "java"
    val jpackagePath = jdkHome.resolve("bin/$jpackageName").absolutePath

    doFirst {
        // Clean previous builds to avoid stale files
        if (outputDir.exists()) outputDir.deleteRecursively()
        
        println(">>> Starting JPackage Build")
        println("Input Directory: ${inputDir.absolutePath}")
        println("Using JDK: ${jdkHome.absolutePath}")
        
        // Execute jpackage
        // --type app-image: Creates a directory structure (not a single installer file)
        // --win-console: Keeps the console window open to allow Ctrl+C signal handling
        commandLine(
            jpackagePath,
            "--type", "app-image",
            "--input", inputDir,
            "--dest", outputDir,
            "--name", appName,
            "--main-jar", "app.jar", // The Gradle 'application' plugin always names the main jar 'app.jar'
            "--main-class", "minecraft.wrapper.App",
            "--win-console",
            "--java-options", "-Xmx64m" // Low memory overhead for the wrapper itself
        )
    }

    // --- Post-Processing Fix ---
    // jpackage creates a stripped-down runtime that usually excludes 'bin/java.exe' 
    // because the native launcher uses the JVM DLL directly.
    // However, our wrapper needs to spawn a *new* Java process for the Minecraft server.
    // Therefore, we must manually copy the 'java' executable from the JDK into the distribution.
    doLast {
        val sourceJava = jdkHome.resolve("bin/$javaName")
        val destJava = outputDir.resolve("$appName/runtime/bin/$javaName")
        
        println(">>> Patching Runtime")
        println("Copying $javaName to allow child process creation...")
        
        if (sourceJava.exists() && destJava.parentFile.exists()) {
            sourceJava.copyTo(destJava, overwrite = true)
            println("Success: Patched ${destJava.absolutePath}")
        } else {
            println("WARNING: Could not find source java executable or destination directory.")
            println("Source: $sourceJava")
            println("Dest: $destJava")
        }
        
        println("\n========================================================")
        println(" Distribution Created Successfully!")
        println(" Location: ${outputDir.resolve(appName)}")
        println(" Executable: ${outputDir.resolve("$appName/$appName.exe")}")
        println("========================================================")
    }
}