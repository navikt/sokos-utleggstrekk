package no.nav.sokos.utleggstrekk.service

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeTilOS
import no.nav.sokos.utleggstrekk.database.model.PerioderTilOS
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode.ENDR
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode.NY
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.DokumentTilOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPM
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPP
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AVSLUTTET

data class MeldingTilOppdrag(
    val transaksjonsID: String,
    val dokument: DokumentTilOppdrag,
)

const val KODE_TREKKTYPE = "TRK1"
const val KILDE = "SOKOSUTLEGG"

// todo: flytt datasource inn RepositoryNy
class BehandleTrekkServiceNy(private val dataSource: HikariDataSource = PostgresDataSource.dataSource) {
    // Ting er allerede lagret i databasen når vi kommer hit

    fun schedule() {
        trekkSomSkalSendes().forEach { trekk ->
            val document = lagTrekkDokument(trekk)
            val dto = OSDto(UUID.randomUUID().toString(), trekk.trekkid, document)
            dataSource.withTransaction { session -> RepositoryNy.insertTransaksjonTilOs(dto, session) }
        }
    }

    fun trekkSomSkalSendes(): List<TrekkFraSkatt> =
        dataSource.withTransaction { session ->
            RepositoryNy.getTrekkSomIkkeErSendt(session)
        }

    fun perioderForTrekkVersjon(trekkFraSkatt: TrekkFraSkatt) =
        dataSource.withTransaction { session ->
            RepositoryNy.getPerioderForTrekkVersjon(trekkFraSkatt.id, trekkFraSkatt.sekvensnummer, trekkFraSkatt.trekkversjon, session)
        }

    fun trekkForTrekkId(trekkFraSkatt: TrekkFraSkatt): List<TrekkFraSkatt> =
        dataSource.withTransaction { session ->
            RepositoryNy.getTrekkFraSkatt(trekkFraSkatt.trekkid, session)
        }

    fun betalingsInformasjonForTrekk(trekkFraSkatt: TrekkFraSkatt): BetalingsinformasjonFraSkatt? =
        dataSource.withTransaction { session ->
            RepositoryNy.getBetalingsinformasjonForTrekk(trekkFraSkatt.id, session)
        }

    fun lagTrekkDokument(trekk: TrekkFraSkatt): Document {
        // For å lage dokument trenger vi perioder som hører til denne trekkversjonen
        val perioderForTrekkversjon = perioderForTrekkVersjon(trekk)

        /*   if (trekk.trekkversjon != 1) {
               lagTrekkPerioderIDB(trekk, perioderForTrekkversjon)
           }*/

        val perioderInformasjon = utledTrekkAlternativForPeriode(perioderForTrekkversjon)

        return lagTrekkDokument(trekk, perioderInformasjon)
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

    fun nyeTrekkTilOs(trekkFraSkatt: TrekkFraSkatt, trekkPerioder: List<PeriodeFraSkatt>): PerioderTilOS =
        dataSource.withTransaction { session ->
            val alternativ =
                buildSet {
                    addAll(trekkPerioder.map { it.trekkAlternativ() }.distinct())
                    addAll(RepositoryNy.getAlternativForTrekk(trekkFraSkatt, session))
                }

            val gamlePerioder =
                alternativ.associateWith { alternativ ->
                    RepositoryNy.getPerioderTilOs(trekkFraSkatt.trekkid, alternativ, session).filterNot { it.isExpired() }
                }

            val nyePerioder = trekkPerioder.filterNot { periode -> gamlePerioder[periode.trekkAlternativ()]?.any { periode.sameAs(it) } ?: false }
            val nyePerioderForOS = alternativ.associateWith { mutableListOf<PeriodeTilOS>() }.toMutableMap()

            nyePerioder.forEach { periode ->
                alternativ.forEach { alternativ ->
                    nyePerioderForOS[alternativ]?.add(PeriodeTilOS(sats = periode.satsFor(alternativ), fom = periode.startdato, tom = periode.sluttdato))
                }
            }

            PerioderTilOS(
                LOPM = nyePerioderForOS[LOPM]?.toList().orEmpty(),
                LOPP = nyePerioderForOS[LOPP]?.toList().orEmpty(),
            )
        }

    /*
    fun lagTrekkPerioderIDB(trekkFraSkatt: TrekkFraSkatt, perioderForTrekkversjon: List<PeriodeFraSkatt>) {
       dataSource.withTransaction { session ->
           // if trekkversjon = 1
           // lag dokument, return
           val trekkAlternativer =
               RepositoryNy.getTrekkAlternativOS(trekkFraSkatt.trekkid, session).toSet() +
                   perioderForTrekkversjon.map { getTrekkAlternativ(it) }.toSet()

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
               trekkAlternativer.forEach { alternativ ->
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
     */

    private fun getTrekkAlternativ(periodeFraSkatt: PeriodeFraSkatt): TrekkAlternativ =
        when {
            periodeFraSkatt.trekkbeloep != null && periodeFraSkatt.trekkprosent == null -> {
                LOPM
            }

            periodeFraSkatt.trekkprosent != null && periodeFraSkatt.trekkbeloep == null -> {
                LOPP
            }
            else -> {
                throw NotImplementedError(
                    "Begge felter fra skatt, beløp og prosent, er null eller utfylt, Trekkalternativ kan ikke fylles ut. Kun et av den er gyldige for en periode",
                )
            }
        }

    fun lagTrekkDokument(trekkFraSkatt: TrekkFraSkatt, perioderInformasjon: List<PeriodeInformasjon>): Document {
        // Må finne: TSSID, Aksjonskode, Trekkalternativ, Perioder
        // kreditorTrekkId skal ha M eller P på slutten

        // Må hente betalingsinformasjonen til trekket for å finne tssid og kid

        val trekkalternativ = perioderInformasjon.first().trekkalternativ
        val betalingsinformasjon: BetalingsinformasjonFraSkatt = betalingsInformasjonForTrekk(trekkFraSkatt) ?: throw Exception("Betalingsinformasjon er null for trekkId=${trekkFraSkatt.id}")

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

        val transaksjonsID = UUID.randomUUID().toString()
        val gyldigTomDato = if (trekkFraSkatt.trekkstatus == AVSLUTTET.name) LocalDate.now().minusDays(1).toString() else null
        val aksjonskode = getAksjonskodeForTrekk(trekkFraSkatt)
        val nyTrekkId = "${trekkFraSkatt.trekkid}${trekkalternativ.value}"

        val tssId = "kreditorIdTss"

        val prioritetfomdato =
            Instant
                .parse(trekkFraSkatt.opprettet)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString()

        //  val tssId = TSSId.getTssId(betalingsinformasjon.betalingsmottaker, betalingsinformasjon.kontonummer)
        val innrapporteringTrekk =
            InnrapporteringTrekk(
                aksjonskode = aksjonskode,
                kreditorIdTss = tssId,
                kreditorTrekkId = nyTrekkId,
                kodeTrekkAlternativ = trekkalternativ,
                kodeTrekktype = KODE_TREKKTYPE,
                kilde = KILDE,
                gyldigTomDato = gyldigTomDato,
                perioder = perioder,
                debitorId = trekkFraSkatt.skyldner,
                kid = betalingsinformasjon.kidnummer,
                kreditorsRef = trekkFraSkatt.saksnummer,
                prioritetFomDato = prioritetfomdato,
            )

        return DokumentTilOppdrag(transaksjonsID, innrapporteringTrekk)
    }

    fun getAksjonskodeForTrekk(trekkFraSkatt: TrekkFraSkatt): Aksjonskode {
        val trekkVersjon = trekkFraSkatt.trekkversjon

        return when (Trekkstatus.valueOf(trekkFraSkatt.trekkstatus)) {
            AKTIV -> {
                // TODO: Kan være NY med trekkversjon != 1
                if (trekkVersjon == 1) {
                    NY
                } else {
                    trekkForTrekkId(trekkFraSkatt).find { it.trekkversjon == trekkVersjon - 1 }?.let { ENDR } ?: NY
                }
            }

            AVSLUTTET -> {
                ENDR
            }
        }
    }
}