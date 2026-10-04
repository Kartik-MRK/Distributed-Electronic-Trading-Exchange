plugins {
    java
}

description = "DETE common domain value objects, enums, and types"

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(libs.junit.jupiter)
}
