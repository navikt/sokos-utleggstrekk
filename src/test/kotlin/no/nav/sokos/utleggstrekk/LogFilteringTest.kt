package no.nav.sokos.utleggstrekk

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.OutputStreamAppender
import io.kotest.assertions.withClue
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mu.KotlinLogging
import org.slf4j.LoggerFactory

@Isolate
class LogFilteringTest :
    FunSpec({
        test("Kontrolltegn er filtrert fra log output.") {
            val illegal =
                setOf(
                    '\u0000',
                    '\u0001',
                    '\u0002',
                    '\u0007',
                    '\u0008',
                    '\u000C',
                    '\u001B',
                    '\u007F',
                )

            val illegalString = buildString { illegal.forEach { append(it) } }
            val log = KotlinLogging.logger {}
            val capture = ByteArrayOutputStream()
            val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
            val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

            val existingConsoleAppender =
                rootLogger
                    .iteratorForAppenders()
                    .asSequence()
                    .filterIsInstance<ConsoleAppender<ILoggingEvent>>()
                    .firstOrNull() ?: error("No ConsoleAppender found to copy configuration from")

            val existingEncoder = existingConsoleAppender.encoder as PatternLayoutEncoder

            // Lag en ny enkoder som vi kan redirecte strømmen til.
            val newEncoder = PatternLayoutEncoder()
            newEncoder.context = loggerContext
            newEncoder.charset = existingEncoder.charset
            newEncoder.pattern = existingEncoder.pattern
            newEncoder.start()

            // appender for å fange loggdata
            val testAppender = OutputStreamAppender<ILoggingEvent>()
            testAppender.context = loggerContext
            testAppender.name = "test-capture-appender"
            testAppender.encoder = newEncoder
            testAppender.outputStream = capture
            testAppender.start()
            rootLogger.addAppender(testAppender)

            try {
                log.info { "Hello$illegalString World" }
                // Vi vil bare ha innholdet i logglinjen, dvs etter :.
                val rendered = capture.toString(StandardCharsets.UTF_8).substringAfterLast(':')

                withClue("Rendered $rendered log output: '\n$rendered\n'") {
                    rendered.shouldContain("Hello World")
                    rendered.any { it in illegal }.shouldBe(false)
                }
            } finally {
                rootLogger.detachAppender(testAppender)
                testAppender.stop()
            }
        }
    })