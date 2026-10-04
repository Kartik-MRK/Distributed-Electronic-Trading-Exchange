plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

description = "DETE account service — authoritative double-entry ledger & balance management"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-security"))

    // Web, Validation, Security & Actuator
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)

    // Database & Persistence
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    // Kafka (Trade Executions consumer & Outbox publisher)
    implementation(libs.spring.kafka)

    // Observability
    implementation(libs.micrometer.prometheus)

    // Testing
    testImplementation(project(":libs:common-test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jqwik)
}
