plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

description = "DETE Matching Engine Service — High performance, deterministic, in-memory matching engine"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-events"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.micrometer.prometheus)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.spring.kafka)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // JMH Microbenchmarking
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator)

    testImplementation(project(":libs:common-test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.jqwik)
    testImplementation(libs.archunit.junit5)
}

tasks.register<JavaExec>("jmhBenchmark") {
    description = "Runs the in-memory matching engine JMH microbenchmark"
    group = "benchmark"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    args = listOf(".*OrderBookBenchmark.*", "-f", "1", "-wi", "1", "-i", "2", "-t", "1")
}
