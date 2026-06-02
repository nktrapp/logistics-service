plugins {
    java
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
    jacoco
    id("org.sonarqube") version "5.1.0.4882"
}

val javaVersion: String by project
val lombokVersion: String by project
val junitVersion: String by project
val assertjVersion: String by project
val mockitoVersion: String by project

allprojects {
    group = "br.furb"
    version = providers.gradleProperty("version").getOrElse("1.0.0-SNAPSHOT")

    repositories {
        mavenCentral()
    }
}

// ─── SonarQube / SonarCloud (free) — coverage from JaCoCo, PR quality-gate decoration ───
sonar {
    properties {
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "https://sonarcloud.io")
        property("sonar.projectKey", System.getenv("SONAR_PROJECT_KEY") ?: "")
        property("sonar.organization", System.getenv("SONAR_ORGANIZATION") ?: "")
        // The sonar-gradle plugin auto-discovers each module's JaCoCo XML report.
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
        }
    }

    val lombokVersion: String by project
    val junitVersion: String by project
    val assertjVersion: String by project
    val mockitoVersion: String by project
    val byteBuddyVersion: String by project

    dependencies {
        compileOnly("org.projectlombok:lombok:$lombokVersion")
        annotationProcessor("org.projectlombok:lombok:$lombokVersion")
        testCompileOnly("org.projectlombok:lombok:$lombokVersion")
        testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

        testImplementation(platform("org.junit:junit-bom:$junitVersion"))
        testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testImplementation("org.assertj:assertj-core:$assertjVersion")
        testImplementation("org.mockito:mockito-core:$mockitoVersion")
        testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")

        // Byte Buddy 1.17.5+ is required for Mockito to generate bytecode / static mocks on JDK 25.
        testImplementation("net.bytebuddy:byte-buddy:$byteBuddyVersion")
        testImplementation("net.bytebuddy:byte-buddy-agent:$byteBuddyVersion")
    }

    tasks.test {
        useJUnitPlatform()
        finalizedBy(tasks.jacocoTestReport)
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
