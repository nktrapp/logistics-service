plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib") version "3.4.4"
}

dependencyManagement {
    imports {
        mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:${property("springCloudAwsVersion")}")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":infrastructure"))

    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.mapstruct:mapstruct:${property("mapstructVersion")}")

    annotationProcessor("org.mapstruct:mapstruct-processor:${property("mapstructVersion")}")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // Testes
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:mongodb:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
}

tasks.bootJar {
    archiveFileName.set("${project.parent?.name}.jar")
}

// ─── Jib — build/push the OCI image with no Docker daemon and no Dockerfile ───
// Defaults are local-friendly; CI overrides the target with -Djib.to.image / -Djib.to.tags.
jib {
    from {
        image = "eclipse-temurin:25-jre"
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
        }
    }
    to {
        image = "logistics-service"
        tags = setOf(project.version.toString())
    }
    container {
        ports = listOf("8080")
        user = "1000:1000"
        jvmFlags = listOf("-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0")
        environment = mapOf("SPRING_PROFILES_ACTIVE" to "prod")
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}
