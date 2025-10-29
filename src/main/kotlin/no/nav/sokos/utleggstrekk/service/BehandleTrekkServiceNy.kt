package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.Periode
import no.nav.sokos.utleggstrekk.database.model.PeriodeStatus.IKKE_SENDT
import no.nav.sokos.utleggstrekk.database.model.PeriodeStatus.SLETTET
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.sameAs
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

        val perioderForTrekkversjon =
            dataSource.withTransaction { session ->
                RepositoryNy.getPerioderForTrekkVersjon(fraSkattTabellId, trekk.sekvensnummer, trekk.trekkversjon, session)
            }

        if (trekk.trekkversjon != 1) {
            lagTrekkPerioderIDB(trekk, perioderForTrekkversjon)
        }

        lagTrekkDokument() // TODO
    }

    data class PeriodeInformasjon(
        val periode: Periode,
        val trekkalternativ: TrekkAlternativ,
    )

    fun lagTrekkPerioderIDB(trekkFraSkatt: TrekkFraSkatt, perioderForTrekkversjon: List<Periode>) {
        dataSource.withTransaction { session ->
            // if trekkversjon = 1
            // lag dokument, return
            val osTrekkAlternativer = RepositoryNy.getTrekkAlternativ(trekkFraSkatt.trekkid, session).toSet()

            val perioderSendtTilOS = RepositoryNy.getPerioderTilOs(trekkFraSkatt.trekkid, session)

            //  filtrere vekk alle perioder som allerede er sendt til OS fordi de trenger vi ikke sende på nytt
            val nyePerioder = perioderForTrekkversjon.filterNot { periode -> perioderSendtTilOS.any { periode.sameAs(it) } }

            // Hvis nytt trekk: Ikke gjør noe av dette

            // Hvis endring på trekk: Hvis ikke endring på periode: Ikke gjør noe av dette

            // Hvis endring på trekk og på periode:
            // Her må vi enten alltid lage periode med 0.0, eller sjekke hva vi har sendt
            // Hvis beløp == 0 og prosent != 0: Sette til LOPP og med prosent, OG lage periode for LOPM hvor beløp = 0
            // Hvis beløp != 0 og prosent == 0: Sette til LOPM med beløp, OG lage periode for LOPP hvor prosent = 0

            // Hvis periode fra versjon 1 mangler i versjon 2 så skal den få status SLETTET
            val perioderSomSkalEndresIOS =
                perioderSendtTilOS.filter { it.isExpired() }.filterNot { periode ->
                    perioderForTrekkversjon.any { it.sameAs(periode) }
                }

            perioderSomSkalEndresIOS.forEach { periode ->
                RepositoryNy.updatePeriodeStatus(periode, SLETTET, session)
            }

            perioderSomSkalEndresIOS.filterNot { it.status == IKKE_SENDT }.forEach { periode ->
                // Sletting vil si å opprette ny periode i samme intervall med sats = 0
                val nyPeriode = periode.copy(sats = 0.0, status = IKKE_SENDT)
                RepositoryNy.insertTrekkForOS(nyPeriode, session)
            }

            nyePerioder.forEach { periode ->
                osTrekkAlternativer.forEach { alternativ ->
                    val nyPeriode =
                        TrekkPeriodeTable(
                            trekkPeriodeTableId = 0,
                            trekkidSke = trekkFraSkatt.trekkid,
                            trekkversjon = trekkFraSkatt.trekkversjon,
                            datoStart = periode.startdato,
                            datoSlutt = periode.sluttdato,
                            sats = periode.satsFor(alternativ),
                            trekkAlternativ = alternativ,
                            kilde = periode.kildeFor(alternativ),
                            status = IKKE_SENDT,
                        )
                    RepositoryNy.insertTrekkForOS(nyPeriode, session)
                }
            }
        }
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