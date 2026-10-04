plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

description = "DETE audit service — to be implemented in its phase"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-security"))
    testImplementation(project(":libs:common-test"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.prometheus)
    testImplementation(libs.spring.boot.starter.test)
}
