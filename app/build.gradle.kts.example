plugins {
    // [Core] Application plugin: Provides standard tasks for running and bundling Java applications.
    application

    // [Dependency Bundling] Shadow Plugin:
    // Creates a single "fat" or "uber" JAR containing the application code AND all dependencies.
    // WHY: Simplifies distribution by packaging everything into one file, avoiding complex classpath setup for the user.
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

// [Configuration] LWJGL Versioning
val lwjglVersion = "3.3.2"

// [Cross-Platform] Dynamic Native Detection
// We detect the OS at build time to choose the correct native libraries.
val osName = System.getProperty("os.name").lowercase()
val lwjglNatives = when {
    osName.contains("win") -> "natives-windows"
    osName.contains("mac") -> "natives-macos"
    else -> "natives-linux"
}

dependencies {
    // Testing framework
    testImplementation(libs.junit)

    // General utility library
    implementation(libs.guava)

    // [Game Library] LWJGL (Lightweight Java Game Library)
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-stb:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")

    // [Natives] Runtime Native Binaries (OS-Specific)
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")
}

java {
    // [Toolchain] Force Java 17 (Minimum for jpackage)
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    // [Entry Point] The main class that starts the game.
    mainClass.set("jvlwjgl.App")
}

// --- TASK CONFIGURATIONS ---

// 1. Configure Shadow JAR (The "Fat" Jar)
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("app-all.jar")
    mergeServiceFiles()
}

// 2. Custom JPackage Task (Distribution)
// Creates a standalone, portable application image for the CURRENT OS.
tasks.register<Exec>("jpackage") {
    dependsOn("shadowJar")

    val jdkHome = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    }.get().metadata.installationPath

    // [Cross-Platform] Determine executable name
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val jpackageExec = if (isWindows) "jpackage.exe" else "jpackage"
    val jpackageTool = jdkHome.dir("bin").file(jpackageExec).asFile.absolutePath

    val inputDir = layout.buildDirectory.dir("libs").get().asFile.absolutePath
    val outputDir = layout.buildDirectory.dir("dist").get().asFile.absolutePath
    val jarName = "app-all.jar"

    // [Execution] Run the jpackage command
    commandLine(
        jpackageTool,
        "--type", "app-image",          // Create directory structure (portable)
        "--dest", outputDir,
        "--input", inputDir,
        "--main-jar", jarName,
        "--main-class", "jvlwjgl.App",
        "--name", "Platformer"
        // "--win-console"              // Windows only: Keep console open
    )

    // [Pre-Clean] specific to this task
    doFirst {
        val distFolder = file(outputDir)
        if (distFolder.exists()) {
            distFolder.deleteRecursively()
        }
        println("Using jpackage from: $jpackageTool")
        println("Target OS Natives: $lwjglNatives")
    }
}

// [Robust Clean]
tasks.named("clean") {
    doFirst {
        // bin folder (in the root of app) should be cleaned
        val binDir = layout.projectDirectory.dir("bin").asFile // get() is not needed**
        if (binDir.exists()) {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            if (isWindows) {
                println("Attempting to force-delete bin directory via shell...")
                exec {
                    commandLine("cmd", "/c", "rmdir", "/s", "/q", binDir.absolutePath)
                    isIgnoreExitValue = true
                }
            } else {
                delete(binDir)
            }
        }


        // build/dist should be cleaned as well
        val distDir = layout.buildDirectory.dir("dist").get().asFile // get() is required.
        if (distDir.exists()) {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            
            if (isWindows) {
                println("Attempting to force-delete dist directory via shell...")
                exec {
                    commandLine("cmd", "/c", "rmdir", "/s", "/q", distDir.absolutePath)
                    isIgnoreExitValue = true
                }
            } else {
                delete(distDir)
            }
        }
    }
}
