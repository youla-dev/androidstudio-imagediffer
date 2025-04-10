fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatformPlugin)
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
    }
}

// Configure project's dependencies
repositories {
    mavenCentral()
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
    jvmToolchain(17)
}

tasks {
    // some bugs with gradle and version
    // version visible in plugins will still be broken 🙃
    jar {
        archiveVersion = properties("pluginVersion").get()
    }
    jarSearchableOptions {
        archiveVersion = properties("pluginVersion").get()
    }
    composedJar {
        archiveVersion = properties("pluginVersion").get()
    }
    buildPlugin {
        archiveVersion = properties("pluginVersion").get()
    }

    runIde {
        // Absolute path to installed target 3.5 Android Studio to use as
        // IDE Development Instance (the "Contents" directory is macOS specific):
//        ideDir.set(file("/Applications/Android Studio.app/Contents"))
    }

    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = properties("pluginUntilBuild")
    }
}

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#configuration.repositories
repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#dependenciesParametrizePlatform
dependencies {
    intellijPlatform {
        val type = properties("platformType")
        val version = properties("platformVersion")

        create(type, version)
    }
}