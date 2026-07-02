import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.17.0"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = "io.github.armanayvazyan"
version = "0.1.4"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.2")
        bundledPlugin("com.intellij.properties")
        plugin("ru.adelf.idea.dotenv:252.23892.201")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }

        // Marketplace "What's New" is filled automatically from CHANGELOG.md:
        // the section matching the current version (or [Unreleased]) is rendered to HTML.
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(project.version.toString()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    // Signing: required by JetBrains Marketplace. Provide via env vars / CI secrets.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // Publishing to JetBrains Marketplace.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        // Our own code uses no @Internal APIs (see DotEnvPlugin / PropsyConfigurable), so fail
        // CI on internal usage. ToolWindowFactory's getAnchor/getIcon/manage are @Experimental
        // and isApplicable/isDoNotActivateOnStart @Deprecated — Kotlin auto-generates bridge
        // overrides we can't avoid, so those levels stay out of the fatal set.
        failureLevel = listOf(
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
        )
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(21)
    targetCompatibility = JavaVersion.toVersion(21)
}

// Reads/writes CHANGELOG.md (Keep a Changelog format).
//   ./gradlew patchChangelog   moves [Unreleased] into a new [<version>] section
//   ./gradlew getChangelog     prints the current version's notes (used in CI)
changelog {
    version = project.version.toString()
    repositoryUrl = "https://github.com/armanayvazyan/propsy"
}
