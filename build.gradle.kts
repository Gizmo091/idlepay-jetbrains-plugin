plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Build/test target only. This decides which IDE `runIde` launches and which platform API stubs
    // we compile against — it does NOT restrict where the plugin can be installed. Universality comes
    // from depending on com.intellij.modules.platform (the module shared by every JetBrains IDE) and
    // using no IDE-specific APIs.
    intellijPlatform {
        intellijIdea("2025.2.6.2")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Runs in ALL JetBrains IDEs (IDEA, PhpStorm, WebStorm, PyCharm, GoLand, RubyMine, CLion,
            // Rider, DataGrip, Android Studio…) from build 252 (2025.2) onward, with no upper bound.
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }
}

// Convenience run configs to launch the plugin inside a locally installed IDE (no extra download):
//   ./gradlew runPhpStorm
// Duplicate the block (pointing localPath at another *.app) for WebStorm, PyCharm, GoLand, etc.
intellijPlatformTesting {
    runIde {
        register("runPhpStorm") {
            localPath.set(file(System.getProperty("user.home") + "/Applications/PhpStorm.app"))
        }
    }
}
