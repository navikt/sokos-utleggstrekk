package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeStatus.IKKE_SENDT
import no.nav.sokos.utleggstrekk.database.model.PeriodeStatus.SLETTET
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.sameAs
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode.ENDR
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode.NY
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode.OPPH
import no.nav.sokos.utleggstrekk.domene.nav.DokumentTilOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPM
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPP
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AVSLUTTET
import no.nav.sokos.utleggstrekk.utils.TSSId

data class MeldingTilOppdrag(
    val transaksjonsID: String,
    val dokument: DokumentTilOppdrag,
)

const val KODE_TREKKTYPE = "TRK1"
const val KILDE = "SOKOSUTLEGG"

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

        // For å lage dokument trenger vi perioder som hører til denne trekkversjonen
        val perioderForTrekkversjon =
            dataSource.withTransaction { session ->
                RepositoryNy.getPerioderForTrekkVersjon(fraSkattTabellId, trekk.sekvensnummer, trekk.trekkversjon, session)
            }

        if (trekk.trekkversjon != 1) {
            lagTrekkPerioderIDB(trekk, perioderForTrekkversjon)
        }

        // Vi trenger så å utlede trekkalternativer for disse periodene
        val perioderInformasjon = utledTrekkAlternativForPeriode(perioderForTrekkversjon)

        lagTrekkDokument(trekk, perioderInformasjon) // TODO
    }

    fun lagTrekkPerioderIDB(trekkFraSkatt: TrekkFraSkatt, perioderForTrekkversjon: List<PeriodeFraSkatt>) {
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

    private fun getTrekkAlternativ(periodeFraSkatt: PeriodeFraSkatt): TrekkAlternativ =
        if (periodeFraSkatt.trekkbeloep != null && periodeFraSkatt.trekkprosent == null) {
            LOPM
        } else if (periodeFraSkatt.trekkprosent != null && periodeFraSkatt.trekkbeloep == null) {
            LOPP
        } else {
            throw NotImplementedError(
                "Begge felter fra skatt, beløp og prosent, er null eller utfylt, Trekkalternativ kan ikke fylles ut. Kun et av den er gyldige for en periode",
            )
        }

    data class PeriodeInformasjon(
        val trekkidSke: String,
        val periodeFraSkatt: PeriodeFraSkatt,
        val trekkalternativ: TrekkAlternativ,
    )

    fun utledTrekkAlternativForPeriode(perioder: List<PeriodeFraSkatt>): List<PeriodeInformasjon> =
        perioder.map { periode ->
            val trekkalternativ = getTrekkAlternativ(periode)
            PeriodeInformasjon(periode.trekkIdSke, periode, trekkalternativ)
        }

    fun lagTrekkDokument(trekkFraSkatt: TrekkFraSkatt, perioderInformasjon: List<PeriodeInformasjon>) {
        // Må finne: TSSID, Aksjonskode, Trekkalternativ, Perioder
        // kreditorTrekkId skal ha M eller P på slutten

        // Må hente betalingsinformasjonen til trekket for å finne tssid og kid

        val trekkalternativ = perioderInformasjon.first().trekkalternativ

        val betalingsinformasjon: BetalingsinformasjonFraSkatt? = dataSource.withTransaction { session -> RepositoryNy.getBetalingsinformasjonForTrekk(trekkFraSkatt.id, session) }

        if (betalingsinformasjon == null) {
            throw Exception("Betalingsinformasjon er null for trekkId=${trekkFraSkatt.id}")
        }
        val perioder =
            Perioder(
                perioderInformasjon.map {
                    val sats: Double =
                        when (it.trekkalternativ) {
                            LOPM -> it.periodeFraSkatt.trekkbeloep ?: error("trekkbeloep is null for LOPM")
                            LOPP -> it.periodeFraSkatt.trekkprosent ?: error("trekkprosent is null for LOPP")
                        }
                    Periode(
                        periodeFomDato = it.periodeFraSkatt.startdato,
                        periodeTomDato = it.periodeFraSkatt.sluttdato ?: "9999-12-31",
                        sats,
                    )
                },
            )

        val aksjonskode = getAksjonskodeForTrekk(trekkFraSkatt)
        val nyTrekkId = "${trekkFraSkatt.trekkid}${trekkalternativ.value}"
        val tssId = TSSId.getTssId(betalingsinformasjon.betalingsmottaker, betalingsinformasjon.kontonummer)
        val innrapporteringTrekk =
            InnrapporteringTrekk(
                aksjonskode = aksjonskode,
                kreditorIdTss = tssId,
                kreditorTrekkId = nyTrekkId,
                debitorId = trekkFraSkatt.skyldner,
                kodeTrekkAlternativ = trekkalternativ,
                kid = betalingsinformasjon.kidnummer,
                kreditorsRef = trekkFraSkatt.saksnummer,
                prioritetFomDato = trekkFraSkatt.opprettet,
                perioder = perioder,
            )
    }

    fun getAksjonskodeForTrekk(trekkFraSkatt: TrekkFraSkatt): Aksjonskode =
        when (Trekkstatus.valueOf(trekkFraSkatt.trekkstatus)) {
            AKTIV -> {
                if (trekkFraSkatt.trekkversjon == 1) {
                    NY
                } else {
                    ENDR
                }
            }
            AVSLUTTET -> {
                OPPH
            }
        }
}