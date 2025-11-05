package no.nav.sokos.utleggstrekk.service

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeTilOS
import no.nav.sokos.utleggstrekk.database.model.PerioderTilOS
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
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
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AVSLUTTET

const val KODE_TREKKTYPE = "TRK1"
const val KILDE = "SOKOSUTLEGG"

class BehandleTrekkServiceNy(private val repositoryNy: RepositoryNy = RepositoryNy(PostgresDataSource.dataSource)) {
    // Ting er allerede lagret i databasen når vi kommer hit

    fun behandleTrekk() =
        repositoryNy.getTrekkSomIkkeErBehandlet().forEach { trekk ->
            val documents = lagTrekkDokument(trekk)

            documents.forEach { document ->
                val documentJson = jsonConfig.encodeToString<DokumentTilOppdrag>(document)
                val dto = OSDto(UUID.randomUUID().toString(), trekk.trekkid, document.innrapporteringTrekk, documentJson)
                runCatching {
                    repositoryNy.insertTransaksjonTilOs(dto)
                }.onSuccess { repositoryNy.updateTrekkFraSkattStatus(trekk.id, SkattTrekkStatus.BEHANDLET) }
            }
        }

    fun lagTrekkDokument(trekk: TrekkFraSkatt): List<Document> {
        // Vi trenger å vite om trekk(ene) er kjent for OS
        val kjenteAlternativ = repositoryNy.getOsAlternativForTrekk(trekk)
        val nyePerioderTilOS = nyePerioderTilOS(trekk)

        return listOf(LOPM, LOPP).mapNotNull { alternativ ->
            when {
                nyePerioderTilOS[alternativ].isEmpty() -> null
                else ->
                    lagTrekkDokument(
                        trekkFraSkatt = trekk,
                        trekkalternativ = alternativ,
                        aksjonskode = if (kjenteAlternativ.contains(alternativ)) ENDR else NY,
                        perioderTilOS = nyePerioderTilOS[alternativ],
                    )
            }
        }
    }

    private fun nyePerioderTilOS(trekkFraSkatt: TrekkFraSkatt): PerioderTilOS {
        val trekkPerioder = repositoryNy.getPerioderForTrekk(trekkFraSkatt)
        // Alternativene som finnes i dette trekket + alternativ fra dokumenter vi har sendt eller skal sende til OS
        val alternativ =
            buildSet {
                addAll(trekkPerioder.map { it.trekkAlternativ() }.distinct())
                addAll(repositoryNy.getOsAlternativForTrekk(trekkFraSkatt))
            }
        // Vi henter kjente osPerioder for å se etter endringer. Bare perioder som er fortsatt gyldige og som har en sats er relevante.
        val osPerioder =
            alternativ.associateWith { alternativ ->
                repositoryNy.getPerioderTilOs(trekkFraSkatt.trekkid, alternativ).filterNot { it.isExpired() || it.sats == 0.0 }
            }
        // De osPeriodene med sats, fortsatt gyldige, men som ikke finnes i trekkFraSkatt gjelder ikke lenger og må nulles i OS.
        val osPerioderIkkeItrekkFraSkatt =
            osPerioder.mapValues { (alternativ, list) ->
                list.filterNot { osPeriode -> trekkPerioder.any { it.trekkAlternativ() == alternativ && it.sameAs(osPeriode) } }
            }
        // For hvert trekkAlternativ har vi en liste over nye perioder til OS.
        val nyePerioderForOS = alternativ.associateWith { mutableListOf<PeriodeTilOS>() }
        // For hver periode i OS som er gyldig og har sats men ikke er med i trekkFra skatt lager vi en ny periode for å nulle den i OS.
        alternativ.forEach { alternativ ->
            osPerioderIkkeItrekkFraSkatt.getValue(alternativ).forEach { periodeBorte ->
                nyePerioderForOS.getValue(alternativ).add(periodeBorte.copy(id = 0L, sats = 0.0, osTransaksjonId = 0L))
            }
        }
        // Vi filterer vekk alle perioder OS kjenner fra før. Disse skal ikke bli nye perioder til OS.
        val nyePerioder = trekkPerioder.filterNot { periode -> osPerioder.getValue(periode.trekkAlternativ()).any { periode.sameAs(it) } }
        // for hver periode ikke kjent for OS, for hvert aktuelle trekkalternativ, lager vi en ny periode.
        nyePerioder.forEach { periode ->
            alternativ.forEach { alternativ ->
                nyePerioderForOS.getValue(alternativ).add(PeriodeTilOS(sats = periode.satsFor(alternativ), periodeFomDato = periode.startdato, periodeTomDato = periode.sluttdato))
            }
        }

        return PerioderTilOS(
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

    fun lagTrekkDokument(
        trekkFraSkatt: TrekkFraSkatt,
        trekkalternativ: TrekkAlternativ,
        aksjonskode: Aksjonskode,
        perioderTilOS: List<PeriodeTilOS>,
    ): Document {
        // Må finne: TSSID, Aksjonskode, Trekkalternativ, Perioder
        // kreditorTrekkId skal ha M eller P på slutten

        // Må hente betalingsinformasjonen til trekket for å finne tssid og kid

        val betalingsinformasjon: BetalingsinformasjonFraSkatt =
            repositoryNy.getBetalingsinformasjonForTrekk(trekkFraSkatt.id) ?: throw Exception("Betalingsinformasjon er null for trekkId=${trekkFraSkatt.id}")

        val perioder =
            Perioder(
                perioderTilOS.map { Periode(it.periodeFomDato, it.periodeTomDato ?: "9999-12-31", it.sats) },
            )

        val transaksjonsID = UUID.randomUUID().toString()
        val gyldigTomDato = if (trekkFraSkatt.trekkstatus == AVSLUTTET.name) LocalDate.now().minusDays(1).toString() else null
        val nyTrekkId = "${trekkFraSkatt.trekkid}${trekkalternativ.value}"

        val tssId = "kreditorIdTss"

        val prioritetfomdato =
            OffsetDateTime
                .parse(trekkFraSkatt.opprettet, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
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
}