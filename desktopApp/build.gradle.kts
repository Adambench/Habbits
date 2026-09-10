import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "io.github.adambench.habbits.MainKt"

        nativeDistributions {
            // Fedora is the primary desktop target; Deb covers Debian/Ubuntu.
            targetFormats(TargetFormat.Rpm, TargetFormat.Deb)
            packageName = "Habbits"
            packageVersion = "1.0.0"
            description = "Offline-first habit logger organised along the daily prayer timeline"
            vendor = "Adambench"
        }
    }
}
