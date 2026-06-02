plugins {
    java
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
    jacoco
}

val javaVersion: String by project
val lombokVersion: String by project
val junitVersion: String by project
val assertjVersion: String by project
val mockitoVersion: String by project

allprojects {
    group = "br.furb"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
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
    }

    tasks.test {
        useJUnitPlatform()
        // ByteBuddy/Mockito do not yet recognize the JDK 25 class-file version; allow it.
        systemProperty("net.bytebuddy.experimental", "true")
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
