import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("org.jetbrains.kotlinx.kover") version "0.9.7"

    application
}

group = "no.nav.sokos"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }

    val githubToken = System.getenv("GITHUB_TOKEN")
    if (githubToken.isNullOrEmpty()) {
        maven {
            name = "external-mirror-github-navikt"
            url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        }
    } else {
        maven {
            name = "github-package-registry-navikt"
            url = uri("https://maven.pkg.github.com/navikt/maven-release")
            credentials {
                username = "token"
                password = githubToken
            }
        }
    }
}

val ktorVersion = "3.4.0"
val kotlinxDatetimeVersion = "0.7.1-0.6.x-compat"
val kotlinxSerializationVersion = "1.10.0"
val nimbusVersion = "10.7"

val vaultVersion = "1.3.10"
val konfigVersion = "1.6.10.0"
val prometheusVersion = "1.16.3"
val unleashedVersion = "12.1.1"

// DB
val hikaricpVersion = "7.0.2"
val flywayVersion = "12.0.1"
val postgresqlVersion = "42.7.10"
val kotliqueryVersion = "2.0.0"

// Logging
val logbackVersion = "1.5.31"
val logstashVersion = "9.0"
val kotlinLoggingVersion = "3.0.5"
val janinoVersion = "3.1.12"

val gsonVersion = "2.13.2"

// Test
val kotestVersion = "6.1.3"
val mockkVersion = "1.14.9"
val commonsVersion = "3.12.0"
val testContainerVersion = "1.21.4"
val activemqVersion = "2.51.0"
val kotestTestContainerExtensionVersion = "2.0.2"
val ibmMqVersion = "9.4.5.0"

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
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
    runtimeOnly("org.codehaus.janino:janino:$janinoVersion")
    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:$logstashVersion")

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
    implementation("no.nav.kotliquery:kotliquery:$kotliqueryVersion")
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
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("org.apache.activemq:artemis-jakarta-server:$activemqVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}

// CVE fixes for vulnerable dependencies from transitive dependencies io.kotest.extensions:kotest-extensions-testcontainers
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.lz4" && requested.name == "lz4-java") {
            useTarget("at.yawk.lz4:lz4-java:1.10.3")
            because("CVE fix: Out-of-bounds memory operations in versions < 1.8.1")
        }
        if (requested.group == "org.xerial.snappy" && requested.name == "snappy-java") {
            useVersion("1.1.10.8")
            because("CVE fix: Unchecked chunk length leads to DoS in versions <= 1.1.10.0")
        }
    }
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
