package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.json.Json

import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag

data class MeldingTilOppdrag(
    val transaksjonsID: String,
    val trekkSomSkalSendes: TrekkTilOppdrag,
)

class BehandleTrekkServiceNy(private val databaseService: DatabaseService) {
    val jsonConfig =
        Json {
            explicitNulls = false
            encodeDefaults = true
        }

    // Ting er allerede lagret i databasen når vi kommer hit

    fun foo() {
        val trekkIkkeBehandlet = databaseService.hentAlleTrekkSomIkkeErBehandlet()

        // Må finne: TSSID, Aksjonskode, Trekkalternativ, Perioder
        // Bør dette lagres i DB? Hvordan? Hvor? Lage tabell som som er 1:1 med det vi sender til OS?
        // Men det bør være lett for oss å debugge når vi får saker fra folk

        // Sende til OS
    }

    fun sendTrekkTilOS(trekkTilOppdragMap: MeldingTilOppdrag) {
        //  mqProducer.send(jsonConfig.encodeToString(trekkTilOppdragMap.trekkSomSkalSendes))
        databaseService.oppdaterTrekkStatus(trekkTilOppdragMap.transaksjonsID, UtleggstrekkStatus.SENDT)
    }
}