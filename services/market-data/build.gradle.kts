plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

description = "DETE market-data service — real-time read-side CQRS, order book, trade tape, OHLCV candles, and WebSocket streaming"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-security"))

    // Web & WebSocket & Security
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation(libs.spring.boot.starter.actuator)

    // Database & Flyway
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    // Kafka
    implementation(libs.spring.kafka)

    // Metrics, Tracing, Logging & JSON
    implementation(libs.micrometer.prometheus)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Testing
    testImplementation(project(":libs:common-test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.archunit.junit5)
    testImplementation("org.awaitility:awaitility:4.2.2")
}
