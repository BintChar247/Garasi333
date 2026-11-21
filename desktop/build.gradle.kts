import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.google.zxing:core:3.5.3")
}

// UI text: reuse the Android app's English strings so both apps say exactly the same thing.
val copyStrings by tasks.registering(Copy::class) {
    from(rootProject.file("app/src/main/res/values")) { include("strings*.xml") }
    into(layout.buildDirectory.dir("generated/strings/garasi"))
}
sourceSets { main { resources.srcDir(layout.buildDirectory.dir("generated/strings")) } }
tasks.named("processResources") { dependsOn(copyStrings) }

compose.desktop {
    application {
        mainClass = "com.garasi.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Garasi"
            packageVersion = "1.0.0"
            description = "Garage-sale listing helper (syncs with the Garasi Android app)"
            vendor = "Garasi"
            includeAllModules = true
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "5b0e6f2a-7d1c-4f3e-9a51-2c8f0d6b4e17"
                iconFile.set(project.file("icon.ico"))
            }
        }
    }
}
