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
val jschVersion = "0.2.12"
val nimbusVersion = "9.37.2"
val postgresqlVersion = "42.6.1"
val flywayVersion = "9.16.1"
val vaultVersion = "1.3.10"
val natpryceVersion = "1.6.10.0"
val kotlinxSerializationVersion = "1.6.0"
val kotlinxDatetimeVersion = "0.4.1"
val prometheusVersion = "1.11.5"
val simpleXmlVersion = "2.7.1"


// Test
val kotestVersion = "5.8.0"
val kotestTestContainerExtensionVersion = "2.0.2"
val mockkVersion = "1.13.8"
val commonsVersion = "3.10.0"
val testContainerVersion = "1.19.1"
val mockFtpServerVersion = "3.1.0"

// Logging
val janinoVersion = "3.1.10"
val kotlinLoggingVersion = "3.0.5"
val logbackVersion = "1.4.12"
val logstashVersion = "7.4"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")

    // Ktor Client
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-apache-jvm:$ktorVersion")

    // Security
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikaricpVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("no.nav:vault-jdbc:$vaultVersion")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:$kotlinxDatetimeVersion")
    implementation("javax.xml.bind:jaxb-api:2.3.1")

    // FTP
    implementation("com.github.mwiede:jsch:$jschVersion")

    // Config
    implementation("com.natpryce:konfig:$natpryceVersion")

    // Flyway
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    //XML
    implementation("org.simpleframework:simple-xml:$simpleXmlVersion")

    // metrics
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
    runtimeOnly("org.codehaus.janino:janino:$janinoVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")

    // Test
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:$kotestTestContainerExtensionVersion")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotestVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("commons-net:commons-net:$commonsVersion")
    testImplementation("org.mockftpserver:MockFtpServer:$mockFtpServerVersion")
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
            attributes["Main-Class"] = "sokos.utleggstrekk.ApplicationKt"
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
