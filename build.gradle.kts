plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.hans"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2")
        bundledPlugin("Git4Idea")
        bundledModule("intellij.platform.vcs.dvcs")
        bundledModule("intellij.platform.vcs.dvcs.impl")
        bundledModule("intellij.platform.vcs.dvcs.impl.shared")
        bundledModule("intellij.platform.vcs.impl")
        bundledModule("intellij.platform.vcs.impl.shared")
        bundledModule("intellij.platform.vcs.log")
        bundledModule("intellij.platform.vcs.log.graph.impl")
        bundledModule("intellij.platform.vcs.log.impl")
        bundledModule("intellij.vcs.git.shared")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.hans.git-merge-into-plus"
        name = "Git Merge Into Plus"
        version = "0.1.0"
        ideaVersion {
            sinceBuild = "261.0"
            untilBuild = "263.*"
        }
    }
}
