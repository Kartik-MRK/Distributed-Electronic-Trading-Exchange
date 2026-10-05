plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

description = "DETE audit service — immutable append-only compliance log"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-security"))

    // Web & Security & Actuator
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)

    // Database & Flyway
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    // Kafka
    implementation(libs.spring.kafka)

    // Metrics & JSON
    implementation(libs.micrometer.prometheus)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Testing
    testImplementation(project(":libs:common-test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.archunit.junit5)
    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation(libs.jjwt.api)
    testImplementation(libs.jjwt.impl)
    testImplementation(libs.jjwt.jackson)
}

