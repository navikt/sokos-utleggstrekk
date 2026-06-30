import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"

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

val ktorVersion = "3.5.0"
val kotlinxDatetimeVersion = "0.7.1-0.6.x-compat"
val kotlinxSerializationVersion = "1.11.0"
val nimbusVersion = "10.9"

val vaultVersion = "1.3.10"
val konfigVersion = "1.6.10.0"
val prometheusVersion = "1.16.5"
val unleashedVersion = "12.2.1"

// DB
val hikaricpVersion = "7.0.2"
val flywayVersion = "12.5.0"
val postgresqlVersion = "42.7.11"
val kotliqueryVersion = "2.0.8"

// Logging
val logbackVersion = "1.5.32"
val logstashVersion = "9.0"
val kotlinLoggingVersion = "3.0.5"
val janinoVersion = "3.1.12"

val gsonVersion = "2.13.2"

// Test
val kotestVersion = "6.1.11"
val mockkVersion = "1.14.9"
val commonsVersion = "3.13.0"
val testContainerVersion = "1.21.4"
val activemqVersion = "2.53.0"
val ibmMqVersion = "9.4.5.1"

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
    implementation("no.nav:kotliquery:$kotliqueryVersion")
    implementation("no.nav:vault-jdbc:$vaultVersion")

    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    // MQ
    implementation("com.ibm.mq:com.ibm.mq.jakarta.client:$ibmMqVersion")

    // Test
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("commons-net:commons-net:$commonsVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("org.apache.activemq:artemis-jakarta-server:$activemqVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}

configurations.all {
    resolutionStrategy {
        eachDependency {

            // High
            if (requested.group == "com.fasterxml.jackson.core" && requested.name == "jackson-core") {
                useVersion("2.21.1")
                because("jackson-core: Number Length Constraint Bypass in Async Parser Leads to Potential DoS Condition. Affected version >= 2.19.0, < 2.21.1")
            }
            if (requested.group == "tools.jackson.core" && requested.name == "jackson-core") {
                useVersion("3.1.1")
                because("Jackson Core: Document length constraint bypass in blocking, async, and DataInput parsers. Affected version >= 3.0.0, <= 3.1.0")
            }
            if (requested.group == "org.xerial.snappy" && requested.name == "snappy-java") {
                useVersion("1.1.10.4")
                because("snappy-java's missing upper bound check on chunk length can lead to Denial of Service (DoS) impact. Affected version <= 1.1.10.3")
            }
            if (requested.group == "io.netty" && requested.name == "netty-codec-http") {
                useVersion("4.2.13.Final")
                because(
                    "CVE-2026-42587: Netty HttpContentDecompressor maxAllocation bypass with br/zstd/snappy leads to decompression bomb DoS. Affected version = 4.2.11.Final, patched in >= 4.2.13.Final",
                )
            }
            if (requested.group == "io.netty") {
                useVersion("4.2.15.Final")
                because("Netty CVE remediation: CVE-2026-45536 and CVE-2026-48043")
            }
            if (requested.group == "org.bouncycastle" && requested.name == "bcprov-jdk18on") {
                useVersion("1.84")
                because("Bouncy Castle Has Covert Timing Channel Vulnerability. Affected version >= 1.71, < 1.84")
            }
            if (requested.group == "org.bouncycastle" && requested.name == "bcpkix-jdk18on") {
                useVersion("1.84")
                because(
                    "CVE-2026-5588: Bouncy Castle Crypto Package For Java - Use of a Broken or Risky Cryptographic Algorithm vulnerability in bcpkix modules. PKIX draft CompositeVerifier accepts empty signature sequence as valid. Affected version >= 1.49, < 1.84",
                )
            }
            if (requested.group == "org.bouncycastle" && requested.name == "bcutil-jdk18on") {
                useVersion("1.84")
                because("Upgrading bcutil-jdk18on to match bcpkix-jdk18on and bcprov-jdk18on versions for consistency")
            }

            // Moderate
            if (requested.group == "com.squareup.okio" && requested.name == "okio") {
                useVersion("3.4.0")
                because("Okio Signed to Unsigned Conversion Error vulnerability. Affected version >= 2.0.0-RC1, < 3.4.0")
            }

            // Test
            if (requested.group == "org.apache.commons" && requested.name == "commons-compress") { // ./gradlew dependencies --configuration testRuntimeClasspath | grep commons-compress
                useVersion("1.26.0")
                because("Apache Commons Compress: OutOfMemoryError unpacking broken Pack200 file. Affected version >= 1.21, < 1.26.0")
            }
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
