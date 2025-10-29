package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.Periode
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPM
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPP
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag

data class MeldingTilOppdrag(
    val transaksjonsID: String,
    val trekkSomSkalSendes: TrekkTilOppdrag,
)

class BehandleTrekkServiceNy(private val dataSource: HikariDataSource = PostgresDataSource.dataSource) {
    // Ting er allerede lagret i databasen når vi kommer hit
    fun run() {
        val trekkSomSkalSendes =
            dataSource.withTransaction { session ->
                RepositoryNy.getTrekkSomIkkeErSendt(session)
            }
        trekkSomSkalSendes.forEach { trekk ->
            lagTrekk(trekk)
        }
    }

    fun lagTrekk(trekk: TrekkFraSkatt) {
        val fraSkattTabellId = trekk.id
        val trekkIdSKE = trekk.trekkid

        val perioderForTrekkversjon =
            dataSource.withTransaction { session ->
                RepositoryNy.getPerioderForTrekkVersjon(fraSkattTabellId, trekk.sekvensnummer, trekk.trekkversjon, session)
            }

        // TODO: Trenger ikke? Vi trenger de trekkene som er sendt til OS
        val allePerioderForTrekk =
            dataSource.withTransaction { session ->
                RepositoryNy.getAllePerioderForTrekkId(trekkIdSKE, session)
            }

        lagTrekkPerioder(trekkIdSKE, perioderForTrekkversjon, allePerioderForTrekk)

        lagTrekkDokument() // TODO
    }

    data class PeriodeInformasjon(
        val periode: Periode,
        val trekkalternativ: TrekkAlternativ,
    )

    // TODO: Er list<Periode> riktig?
    fun lagTrekkPerioder(trekkID: String, perioderForTrekkversjon: List<Periode>, allePerioderForTrekk: List<Periode>): List<Periode> {
        // Vi må vite trekkalternativ til periodene
        return emptyList()
    }

    private fun lagPeriodeInformasjon(periode: Periode): PeriodeInformasjon = PeriodeInformasjon(periode, getTrekkAlternativ(periode))

    fun getTrekkAlternativ(periode: Periode): TrekkAlternativ =
        if (periode.trekkbeloep != null && periode.trekkprosent == null) {
            LOPM
        } else if (periode.trekkprosent != null && periode.trekkbeloep == null) {
            LOPP
        } else {
            throw NotImplementedError(
                "Begge felter fra skatt, beløp og prosent, er null eller utfylt, Trekkalternativ kan ikke fylles ut. Kun et av den er gyldige for en periode",
            )
        }

    fun lagTrekkDokument() {
        // Må finne: TSSID, Aksjonskode, Trekkalternativ, Perioder
        // Bør dette lagres i DB? Hvordan? Hvor? Lage tabell som som er 1:1 med det vi sender til OS?
        // Men det bør være lett for oss å debugge når vi får saker fra folk
    }
}