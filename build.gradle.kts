import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "no.nav.sokos"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

val ktorVersion = "3.2.3"
val kotlinxDatetimeVersion = "0.7.1-0.6.x-compat"
val kotlinxSerializationVersion = "1.9.0"
val nimbusVersion = "10.4.2"

val vaultVersion = "1.3.10"
val konfigVersion = "1.6.10.0"
val prometheusVersion = "1.15.3"

// DB
val hikaricpVersion = "7.0.2"
val flywayVersion = "11.11.2"
val postgresqlVersion = "42.7.7"
val kotliqueryVersion = "1.9.1"

// Logging
val logbackVersion = "1.5.18"
val logstashVersion = "8.1"
val kotlinLoggingVersion = "3.0.5"

val gsonVersion = "2.13.1"

// Test
val kotestVersion = "6.0.1"
val mockkVersion = "1.14.5"
val commonsVersion = "3.12.0"
val testContainerVersion = "1.21.3"
val activemqVersion = "2.40.0"
val kotestTestContainerExtensionVersion = "2.0.2"
val janinoVersion = "3.1.12"
val ibmMqVersion = "9.4.3.0"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")

    // Ktor Client
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    // Logging
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    runtimeOnly("org.codehaus.janino:janino:$janinoVersion")

    // metrics
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
    implementation("com.natpryce:konfig:$konfigVersion")

    // Security
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikaricpVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("no.nav:vault-jdbc:$vaultVersion")

    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    // MQ
    implementation("com.ibm.mq:com.ibm.mq.jakarta.client:$ibmMqVersion")

    //
    implementation("com.google.code.gson:gson:$gsonVersion")

    // Test
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:$kotestTestContainerExtensionVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("commons-net:commons-net:$commonsVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.apache.activemq:artemis-jakarta-server:$activemqVersion")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
configurations.ktlint {
    resolutionStrategy.force("ch.qos.logback:logback-classic:$logbackVersion")
}

sourceSets {
    main {
        java {
            srcDirs("${layout.buildDirectory.get()}/generated/src/main/kotlin")
        }
    }
}

tasks {

    withType<KotlinCompile>().configureEach {
        dependsOn("ktlintFormat")
    }
    withType<ShadowJar>().configureEach {
        enabled = true
        archiveFileName.set("sokos-utleggstrekk.jar")
        manifest {
            attributes["Main-Class"] = "no.nav.sokos.utleggstrekk.ApplicationKt"
        }

        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
        finalizedBy(koverHtmlReport)
    }

    withType<Test>().configureEach {
        jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
        systemProperty("isTest", "true")
        useJUnitPlatform()
        testLogging {
            showExceptions = true
            showStackTraces = true
            exceptionFormat = FULL
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
        reports.forEach { report -> report.required.value(false) }
    }

    withType<Wrapper> {
        gradleVersion = "8.9"
    }

    ("jar") {
        enabled = false
    }

    ("build") {
        dependsOn("copyPreCommitHook")
    }

    register<Copy>("copyPreCommitHook") {
        from(".scripts/pre-commit")
        into(".git/hooks")
        filePermissions {
            user {
                execute = true
            }
        }
        doFirst {
            println("Installing git hooks...")
        }
        doLast {
            println("Git hooks installed successfully.")
        }
        description = "Copy pre-commit hook to .git/hooks"
        group = "git hooks"
        outputs.upToDateWhen { false }
    }
}