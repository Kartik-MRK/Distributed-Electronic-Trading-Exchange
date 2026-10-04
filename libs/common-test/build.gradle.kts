plugins {
    `java-library`
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
}

description = "DETE shared Testcontainers base classes and test builders"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
        mavenBom("org.testcontainers:testcontainers-bom:${libs.versions.testcontainers.get()}")
    }
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-events"))

    // Testcontainers
    api(libs.testcontainers.core)
    api(libs.testcontainers.junit5)
    api(libs.testcontainers.kafka)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.redis)

    // Spring test support
    api(libs.spring.boot.starter.test)
    api(libs.spring.kafka.test)
    api(libs.kafka.clients)

    // JUnit 5
    api("org.junit.jupiter:junit-jupiter-api")
    api("org.junit.jupiter:junit-jupiter-params")
}

// common-test is a test-support lib, not an app — disable bootJar
tasks.named("jar") { enabled = true }
