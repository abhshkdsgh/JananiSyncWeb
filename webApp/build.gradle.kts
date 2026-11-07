import java.security.MessageDigest
import java.net.URLEncoder

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        useEsModules()
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.navigation.compose)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}

// Keep the public, authentication-free guide in sync with the canonical manual.
val generateUserManual = tasks.register<Exec>("generateUserManual") {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/generate_user_manual.py")
    inputs.files(
        rootProject.file("USER_MANUAL.md"),
        rootProject.file("scripts/generate_user_manual.py"),
        rootProject.file("scripts/user_manual_template.html")
    )
    outputs.file(layout.projectDirectory.file("src/jsMain/resources/user-manual.html"))
}

tasks.named("jsProcessResources") {
    dependsOn(generateUserManual)
}

// Development and production links share the npm package's kotlin/ directory.
// Always restore the selected mode before webpack; otherwise a prior production
// build can leave tree-shaken runtime exports beside development application modules.
tasks.matching {
    it.name in setOf(
        "jsDevelopmentExecutableCompileSync",
        "jsProductionExecutableCompileSync"
    )
}.configureEach {
    outputs.upToDateWhen { false }
}

// Run after webpack and Compose resources have been copied.
tasks.withType<Sync>().matching {
    it.name == "jsBrowserDistribution"
}.configureEach {
    doLast {
        val root = destinationDir
        val assets = root.walkTopDown().filter {
            it.isFile && it.name !in setOf("sw.js", "pwa-assets.json") &&
                it.extension != "map"
        }.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.toList()
        check(assets.any { it.relativeTo(root).invariantSeparatorsPath == "index.html" })
        val digest = MessageDigest.getInstance("SHA-256")
        assets.forEach {
            digest.update(it.relativeTo(root).invariantSeparatorsPath.toByteArray())
            digest.update(it.readBytes())
        }
        val worker = root.resolve("sw.js")
        digest.update(worker.readBytes())
        val version = digest.digest().joinToString("") { "%02x".format(it) }
        // Encode each URL path segment; filenames may contain spaces or non-ASCII characters.
        val paths = assets.map { file ->
            file.relativeTo(root).invariantSeparatorsPath.split('/').joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
        }
        root.resolve("pwa-assets.json").writeText(paths.joinToString(",\n", "[\n", "\n]\n") { "\"$it\"" })
        worker.writeText(worker.readText().replace("PWA_BUILD_VERSION", version))
    }
}
