plugins {
    alias(libs.plugins.spring.dependency.management)
    java
}

description = "DETE Performance Benchmarks — JMH microbenchmarks for matching engine and core primitives"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-events"))
    implementation(project(":services:matching-engine"))

    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator)
}

tasks.register<JavaExec>("jmh") {
    description = "Runs all JMH microbenchmarks"
    group = "benchmark"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    val userArgs = project.findProperty("jmhArgs")?.toString()?.split(" ")?.filter { it.isNotBlank() }
    args = userArgs ?: listOf(
        "-f", "1",
        "-wi", "1",
        "-i", "2",
        "-t", "1"
    )
}

tasks.register<JavaExec>("loadSim") {
    description = "Runs the local exchange multi-threaded load simulator on bare metal"
    group = "benchmark"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.dete.benchmark.LocalExchangeLoadSimulator")
}

