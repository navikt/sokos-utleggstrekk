package no.nav.sokos.utleggstrekk.domene.nav.scheduling

import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

class UtleggstrekkScheduler(private val scope: CoroutineScope) {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var future: ScheduledFuture<*>? = null

    fun scheduleHourlyAt(minute: Int, second: Int = 0, task: suspend () -> Unit) {
        logger.info("Scheduler started on the hour at HH:${minute.twoPad()}:${second.twoPad()}")
        scheduleNext(hour = 0, minute, second, task)
    }

    fun scheduleDailyAt(hour: Int, minute: Int = 0, task: suspend () -> Unit) {
        logger.info("Scheduler started at ${hour.twoPad()}:${minute.twoPad()}:SS")
        scheduleNext(hour, minute, 0, task)
    }

    private fun scheduleNext(
        hour: Int,
        minute: Int,
        second: Int,
        task: suspend () -> Unit,
    ) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(second)

        if (!next.isAfter(now)) {
            next = next.plusHours(1)
        }

        val delay = Duration.between(now, next).toMillis()

        future =
            executor.schedule({
                scope.launch {
                    try {
                        task()
                    } catch (e: Exception) {
                        logger.error("Scheduled job failed: ", e)
                    } finally {
                        scheduleNext(hour, minute, second, task) // chain to next run
                    }
                }
            }, delay, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        future?.cancel(false)
        executor.shutdown()
    }
}

fun Int.twoPad() = "%02d".format(this)