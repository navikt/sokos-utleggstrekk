package no.nav.sokos.utleggstrekk.mockexamples

import mu.KLogger
import mu.KotlinLogging

/*
 * Subject for UnmockkObjectDoesNotResetValDemoTest.
 *
 * The logger is an injected dependency — set once via setLogger() before first use.
 * This gives UnmockkSetupSpec full control over which KLogger instance gets stored,
 * without relying on mockkObject intercepting the KotlinLogging factory.
 *
 * Once set, the stored logger reference is permanent — setLogger() only takes effect
 * the first time it is called (lazy-style via null guard).
 * This mirrors the frozen-val behaviour of `private val logger = KotlinLogging.logger {}`:
 * whatever logger was injected first is what doWork() uses forever.
 *
 * unmockkObject cannot reach back and update an injected reference any more than it
 * can update a val that was evaluated while the mock was active.
 */

object LocalServiceForUnmockk {
    private var injectedLogger: KLogger? = null

    /**
     * Inject a logger before first use. Subsequent calls are ignored — the first
     * logger set is permanent, matching the frozen-val semantics being demonstrated.
     */
    fun setLogger(logger: KLogger) {
        if (injectedLogger == null) injectedLogger = logger
    }

    /** Reset the injected logger — only for use in afterSpec cleanup. */
    fun resetLogger() {
        injectedLogger = null
    }

    private val activeLogger: KLogger
        get() = injectedLogger ?: KotlinLogging.logger { }

    fun doWork() {
        activeLogger.info { "LocalServiceForUnmockk is doing work" }
    }

    fun getLogger(): KLogger = activeLogger
}
