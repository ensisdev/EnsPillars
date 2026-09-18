plugins {
    kotlin("jvm") version "2.1.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.ensisdev"
version = "1.0.0"

description = "EnsPillars - Advanced Pillars of Fortune minigame"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("fr.mrmicky:fastboard:2.1.4")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation(kotlin("stdlib"))
    // Journey-kanıt testleri (10/10 kapatma turu, seçenek b): JUnit5 + MockK.
    testImplementation("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:1.13.13")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    processResources {
        filesMatching("plugin.yml") { expand("version" to project.version) }
    }
    shadowJar {
        archiveClassifier.set("")
        relocate("kotlin", "dev.ensisdev.enspillars.libs.kotlin")
        relocate("fr.mrmicky.fastboard", "dev.ensisdev.enspillars.libs.fastboard")
        relocate("com.zaxxer.hikari", "dev.ensisdev.enspillars.libs.hikari")
        relocate("org.sqlite", "dev.ensisdev.enspillars.libs.sqlite")
        relocate("com.mysql", "dev.ensisdev.enspillars.libs.mysql")
        relocate("org.slf4j", "dev.ensisdev.enspillars.libs.slf4j")
        relocate("org.bstats", "dev.ensisdev.enspillars.libs.bstats")
    }
    build {
        dependsOn(shadowJar)
    }
    withType<Test> {
        useJUnitPlatform()
    }
}
