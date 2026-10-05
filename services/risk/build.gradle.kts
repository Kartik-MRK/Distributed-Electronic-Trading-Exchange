plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

description = "DETE risk service — real-time pre-trade risk validation via gRPC and Kafka event tracking"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-events"))

    // Web & Actuator
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)

    // Kafka
    implementation(libs.spring.kafka)

    // gRPC
    implementation(libs.grpc.netty)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)

    // Resilience & Observability
    implementation(libs.resilience4j.spring.boot3)
    implementation(libs.resilience4j.micrometer)
    implementation(libs.micrometer.prometheus)

    // JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Testing
    testImplementation(project(":libs:common-test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.archunit.junit5)
}
