import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("org.jetbrains.kotlinx.kover") version "0.9.4"

    application
}

group = "no.nav.sokos"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

val ktorVersion = "3.3.3"
val kotlinxDatetimeVersion = "0.7.1-0.6.x-compat"
val kotlinxSerializationVersion = "1.9.0"
val nimbusVersion = "10.6"

val vaultVersion = "1.3.10"
val konfigVersion = "1.6.10.0"
val prometheusVersion = "1.16.1"
val unleashedVersion = "11.2.0"

// DB
val hikaricpVersion = "7.0.2"
val flywayVersion = "11.19.0"
val postgresqlVersion = "42.7.8"
val kotliqueryVersion = "1.9.1"

// Logging
val logbackVersion = "1.5.21"
val logstashVersion = "9.0"
val kotlinLoggingVersion = "3.0.5"

val gsonVersion = "2.13.2"

// Test
val kotestVersion = "6.0.7"
val mockkVersion = "1.14.7"
val commonsVersion = "3.12.0"
val testContainerVersion = "1.21.3"
val activemqVersion = "2.44.0"
val kotestTestContainerExtensionVersion = "2.0.2"
val janinoVersion = "3.1.12"
val ibmMqVersion = "9.4.4.0"

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
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
    implementation("com.natpryce:konfig:$konfigVersion")

    // Feature switches
    implementation("io.getunleash:unleash-client-java:$unleashedVersion")

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
    testImplementation("net.bytebuddy:byte-buddy:1.18.2") // TEMP: Needed for mockk 1.14.6 with java25. Remove when Mockk is updated and bytebuddy is no longer needed.
    testImplementation("org.apache.activemq:artemis-jakarta-server:$activemqVersion")
}

application {
    mainClass.set("no.nav.sokos.utleggstrekk.ApplicationKt")
}

sourceSets {
    main {
        java {
            srcDirs("${layout.buildDirectory.get()}/generated/src/main/kotlin")
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {

    withType<KotlinCompile>().configureEach {
        dependsOn("ktlintFormat")
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

        finalizedBy(koverHtmlReport)
    }

    withType<Wrapper> {
        gradleVersion = "9.2.1"
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
