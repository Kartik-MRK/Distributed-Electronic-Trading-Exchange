plugins {
    java
}

description = "DETE Kafka event schema definitions shared across all services"

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(libs.junit.jupiter)
}
