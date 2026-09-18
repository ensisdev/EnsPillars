pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    // JDK 17 toolchain otomatik indirimi (makinede yüklü değilse Foojay'den çeker).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "EnsPillars"
