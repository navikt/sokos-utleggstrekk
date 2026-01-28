package no.nav.sokos.utleggstrekk.scheduling

import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.TEAM_LOGS_MARKER
import no.nav.sokos.utleggstrekk.service.SlackService

private val logger = KotlinLogging.logger { }

class UtleggstrekkScheduler(private val scope: CoroutineScope) {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var future: ScheduledFuture<*>? = null

    fun scheduleHourlyAt(
        minute: Int,
        second: Int = 0,
        name: String? = null,
        task: suspend () -> Unit,
    ) {
        logger.info("Scheduler for '$name' started on the hour at HH:${minute.twoPad()}:${second.twoPad()}")
        scheduleNext(minute = minute, second = second, name = name, task = task)
    }

    fun scheduleDailyAt(
        hour: Int,
        minute: Int = 0,
        name: String? = null,
        task: suspend () -> Unit,
    ) {
        logger.info("Scheduler started at ${hour.twoPad()}:${minute.twoPad()}:SS")
        scheduleNext(hour, minute, 0, name, task)
    }

    private fun scheduleNext(
        hour: Int? = null,
        minute: Int? = null,
        second: Int? = null,
        name: String? = null,
        task: suspend () -> Unit,
    ) {
        val now = LocalDateTime.now()
        var next =
            now
                .withHour(hour ?: now.hour)
                .withMinute(minute ?: now.minute)
                .withSecond(second ?: 0)
                .withNano(0)

        while (next.isBefore(now)) {
            if (hour != null) {
                next = next.plusDays(1)
            } else if (minute != null) {
                next = next.plusHours(1)
            } else if (second != null) {
                next = next.plusMinutes(1)
            }
        }

        logger.info("Next ${if (name != null) "'$name' " else ""}job scheduled at " + next)

        val delay = Duration.between(now, next).toMillis()

        future =
            executor.schedule({
                scope.launch {
                    try {
                        task()
                    } catch (e: Exception) {
                        logger.error(TEAM_LOGS_MARKER, "Scheduled job failed: ", e)
                        SlackService.instance.addError("Scheduled job failed", "Scheduled job failed: ${e.message}")
                        SlackService.instance.sendCachedErrors("Scheduled job failed")
                    } finally {
                        scheduleNext(hour, minute, second, name = name, task) // chain to next run
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