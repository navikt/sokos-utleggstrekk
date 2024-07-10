import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "no.nav.sokos"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

val ktorVersion = "2.3.6"
val hikaricpVersion = "5.1.0"
val oracleJdbcVersion = "19.22.0.0"
val nimbusVersion = "9.37.2"
val vaultVersion ="1.3.10"
val logback_version = "1.5.6"
val logstash_version = "7.3"
val kotlin_logging_version = "3.0.4"
val kotlinxDatetimeVersion = "0.4.1"
val natpryceVersion = "1.6.10.0"
val prometheus_version = "1.11.0"
val kotlinxSerializationVersion = "1.6.0"
val prometheusVersion = "1.11.5"
val flywayVersion = "9.16.1"
val postgresqlVersion = "42.6.1"

// Test
val kotestVersion = "5.8.0"
val kotestTestContainerExtensionVersion = "2.0.2"
val mockkVersion = "1.13.8"
val commonsVersion = "3.10.0"
val testContainerVersion = "1.19.1"
val mockFtpServerVersion = "3.1.0"
val swaggerRequestValidatorVersion = "2.40.0"
val janinoVersion = "3.1.10"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")

    // Ktor Client
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-apache-jvm:$ktorVersion")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")

    //Logging
    implementation("ch.qos.logback:logback-core:$logback_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstash_version")
    implementation("io.github.microutils:kotlin-logging-jvm:$kotlin_logging_version")
    runtimeOnly("org.codehaus.janino:janino:$janinoVersion")

    // metrics
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:$kotlinxDatetimeVersion")
    implementation("com.natpryce:konfig:$natpryceVersion")

    // Security
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikaricpVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("no.nav:vault-jdbc:$vaultVersion")

    // Flyway
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    // Test
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:$kotestTestContainerExtensionVersion")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotestVersion")
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("commons-net:commons-net:$commonsVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
sourceSets {
    main {
        java {
            srcDirs("${layout.buildDirectory.get()}/generated/src/main/kotlin")
        }
    }
}

tasks {

    withType<ShadowJar>().configureEach {
        enabled = true
        archiveFileName.set("sokos-utleggstrekk.jar")
        manifest {
            attributes["Main-Class"] = "no.nav.sokos.utleggstrekk.ApplicationKt"
        }
    }

    ("jar") {
        enabled = false
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events("passed", "skipped", "failed")
        }
        reports.forEach { report -> report.required.value(false) }
    }

    withType<Wrapper> {
        gradleVersion = "8.4"
    }
}
