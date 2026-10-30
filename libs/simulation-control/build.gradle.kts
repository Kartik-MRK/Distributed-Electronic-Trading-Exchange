plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}") }
}

dependencies {
    api(project(":libs:common-domain"))
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.web)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":libs:common-test"))
}
