import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spotless) apply false
    java
}

// ---------------------------------------------------------------------------
// Spotless formatting — applied to every subproject
// ---------------------------------------------------------------------------
subprojects {
    apply(plugin = "java")
    // Only apply Spotless formatter to projects that actually have Java sources
    if (file("src/main/java").exists() || file("src/test/java").exists()) {
        apply(plugin = "com.diffplug.spotless")
    }

    group = "com.dete"
    version = "0.1.0-SNAPSHOT"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    if (file("src/main/java").exists() || file("src/test/java").exists()) {
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            java {
                googleJavaFormat("1.22.0")
                removeUnusedImports()
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }

    repositories {
        mavenCentral()
    }

    // Skip bootJar for stub subprojects that don't have main classes yet
    tasks.matching { it.name == "bootJar" }.configureEach {
        onlyIf { file("src/main/java").exists() }
    }

    // ---------------------------------------------------------------------------
    // All subprojects get these common test settings
    // ---------------------------------------------------------------------------
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = false
        }
        // Allow parallel test execution
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        // Give Testcontainers room and configure modern Docker API version
        jvmArgs("-Xmx512m", "-Dapi.version=1.44")
        systemProperty("api.version", "1.44")
        environment("DOCKER_API_VERSION", "1.44")
    }

    // ---------------------------------------------------------------------------
    // Shared dependency versions via Spring BOM in every Spring subproject
    // ---------------------------------------------------------------------------
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.springframework.boot") {
                useVersion(libs.versions.spring.boot.get())
            }
        }
    }
}
