pluginManagement {
    val springBootVersion: String by settings
    val springDependencyManagementVersion: String by settings
    plugins {
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version springDependencyManagementVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "logistics-service"

include("domain", "core", "infrastructure", "app")
