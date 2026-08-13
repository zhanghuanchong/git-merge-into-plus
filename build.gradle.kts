plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.hans"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellij {
    version.set("2026.2")
    type.set("IC")
    plugins.set(listOf("Git4Idea"))
    updateSinceUntilBuild.set(false)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
    patchPluginXml {
        sinceBuild.set("261.0")
        untilBuild.set("263.*")
    }
    publishPlugin {
        token.set(System.getenv("JETBRAINS_TOKEN"))
    }
}
