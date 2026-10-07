plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

description = "DETE order service — order management, pre-trade validation, outbox commands & event tracking"

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
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Database & Persistence
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    // Redis for Distributed Idempotency Locking
    implementation(libs.spring.boot.starter.data.redis)

    // Kafka (Commands outbox publisher & order.events consumer)
    implementation(libs.spring.kafka)

    // Resilience4j for circuit breakers
    implementation(libs.resilience4j.spring.boot3)

    // gRPC Client
    implementation(libs.grpc.netty)
    implementation(libs.grpc.stub)

    // Observability
    implementation(libs.micrometer.prometheus)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.logstash.logback.encoder)

    // JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Testing
    testImplementation(project(":libs:common-test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jqwik)
    testImplementation(libs.jjwt.api)
    testImplementation(libs.jjwt.impl)
    testImplementation(libs.jjwt.jackson)
}
