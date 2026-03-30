package no.nav.sokos.utleggstrekk.utils

import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.TEAM_LOGS_MARKER
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ

private val logger = KotlinLogging.logger { }

object SyntetiskId {
    // Private: only used internally for UUID v4 format detection.
    private val UUID_MATCHER = Regex("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-4[0-9A-Fa-f]{3}-[89ABab][0-9A-Fa-f]{3}-[0-9A-Fa-f]{12}$")

    fun syntetiskTrekkId(trekkId: String): String {
        val digest =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(trekkId.toByteArray(Charsets.UTF_8))

        // 24 bytes \-\> 32 Base64 chars (with URL\-safe alphabet) and no padding.
        val truncated = digest.copyOfRange(0, 24)

        return java.util.Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(truncated)
    }

    fun konverterTrekkId(trekkId: String, trekkalternativ: TrekkAlternativ): String =
        when {
            trekkId.isUuidV4() -> "${trekkId.replace("-", "")}${trekkalternativ.suffix}"
            trekkId.length > 34 -> {
                logger.info(TEAM_LOGS_MARKER) { "Ukjent format lang id på trekk. Genererer syntetisk trekkId for trekk $trekkId" }
                "${syntetiskTrekkId(trekkId)}-${trekkalternativ.suffix}"
            }
            else -> "${trekkId}${trekkalternativ.suffix}"
        }

    private fun String.isUuidV4(): Boolean {
        if (length != 36) return false
        return UUID_MATCHER.matches(this)
    }
}
