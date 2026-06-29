plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.4.0"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
    jacoco
    id("com.adarshr.test-logger") version "4.0.0"
}

group = "ai.koalla"
version = "0.3.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "2.0.0"

dependencies {
    // Spring Boot Core
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")

    // Spring AI (OpenAI + PGVector)
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")

    // HTTP Client for external APIs (Chatwoot, Asaas)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Scheduling
    implementation("org.springframework.boot:spring-boot-starter-quartz")

    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")

    // Observability — Micrometer + Prometheus
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.h2database:h2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.amshove.kluent:kluent:1.73")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.1")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// ── Ktlint ────────────────────────────────────────────────────────────────────

ktlint {
    version.set("1.5.0")
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}

// ── Jacoco ────────────────────────────────────────────────────────────────────

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.28".toBigDecimal()
            }
        }
    }
}

// ── Test Logger ───────────────────────────────────────────────────────────────

testlogger {
    theme = com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA
    showExceptions = true
    showStackTraces = true
    showFullStackTraces = false
    showCauses = true
    slowThreshold = 2000
    showSummary = true
    showSimpleNames = false
    showPassed = true
    showSkipped = true
    showFailed = true
    showOnlySlow = false
    showStandardStreams = false
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = true
    logLevel = LogLevel.LIFECYCLE
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)

    // JVM args for Java module system compatibility
    jvmArgs(
        "--add-opens",
        "java.base/java.time=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.lang.reflect=ALL-UNNAMED",
        "-Dspring.profiles.active=test",
    )

    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ── Integration Tests Task ────────────────────────────────────────────────────
// Integration tests use @Tag("integration") annotation
// Run with: ./gradlew integrationTest
// See src/test/kotlin/ai/koalla/core/integration/README.md for setup

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests tagged with @Tag(\"integration\")."
    group = "verification"

    useJUnitPlatform {
        includeTags("integration")
    }

    mustRunAfter(tasks.test)

    // Dynamic heap settings: smaller for CI runners, larger for local development
    val isCI = System.getenv("CI") == "true"
    minHeapSize = if (isCI) "256m" else "512m"
    maxHeapSize = if (isCI) "1G" else "2G"

    jvmArgs(
        "--add-opens",
        "java.base/java.time=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.lang.reflect=ALL-UNNAMED",
        "-Dspring.profiles.active=test",
        "-XX:+UseG1GC",
    )
}

// Exclude integration tests from regular test task
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}
