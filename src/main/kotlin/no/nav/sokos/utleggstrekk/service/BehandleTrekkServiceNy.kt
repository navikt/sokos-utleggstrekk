package no.nav.sokos.utleggstrekk.service

import java.time.LocalDate
import java.util.UUID

import kotliquery.TransactionalSession

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

            repositoryNy.withTransaction { session ->
                val documents = lagTrekkDokument(trekk, session)

                documents.forEach { document ->
                    val documentJson = jsonConfig.encodeToString<DokumentTilOppdrag>(document)
                    val dto = OSDto(document.transaksjonsId, trekk.trekkid, document.innrapporteringTrekk, documentJson)
                    repositoryNy.insertTransaksjonTilOs(dto, session)
                    repositoryNy.updateTrekkFraSkattStatus(trekk.id, SkattTrekkStatus.BEHANDLET, session = session)
                }
            }
        }

    private fun lagTrekkDokument(trekk: TrekkFraSkatt, session: TransactionalSession): List<Document> {
        // Vi trenger å vite om trekk(ene) er kjent for OS
        val kjenteAlternativ = repositoryNy.getOsAlternativForTrekk(trekk, session)
        val nyePerioderTilOS = nyePerioderTilOS(trekk, session)

        return nyePerioderTilOS.alternativ.map { alternativ ->
            lagTrekkDokument(
                trekkFraSkatt = trekk,
                trekkalternativ = alternativ,
                aksjonskode = if (kjenteAlternativ.contains(alternativ)) ENDR else NY,
                perioderTilOS = if (trekk.trekkstatus == AVSLUTTET.name) emptyList() else nyePerioderTilOS[alternativ],
            )
        }
    }

    private fun nyePerioderTilOS(trekkFraSkatt: TrekkFraSkatt, session: TransactionalSession): PerioderTilOS {
        val trekkPerioder = repositoryNy.getPerioderForTrekk(trekkFraSkatt)
        // Alternativene som finnes i dette trekket + alternativ fra dokumenter vi har sendt eller skal sende til OS
        val alternativ =
            buildSet {
                addAll(trekkPerioder.map { it.trekkAlternativ() }.distinct())
                addAll(repositoryNy.getOsAlternativForTrekk(trekkFraSkatt, session))
            }

        // Vi henter kjente osPerioder for å se etter endringer. Bare perioder som er fortsatt gyldige og som har en sats er relevante.
        val osPerioder =
            alternativ.associateWith { alternativ ->
                val allePerioder = repositoryNy.getPerioderTilOs(trekkFraSkatt.trekkid, alternativ)
                allePerioder.filterNot { obsoleted(allePerioder, it) }
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
            alternativ,
            LOPM = nyePerioderForOS[LOPM]?.toList().orEmpty(),
            LOPP = nyePerioderForOS[LOPP]?.toList().orEmpty(),
        )
    }

    private fun lagTrekkDokument(
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

        val perioder = Perioder(perioderTilOS.map { it.asPeriode() }).takeUnless { it.periode.isEmpty() }

        val transaksjonsID = UUID.randomUUID().toString()
        val gyldigTomDato = if (trekkFraSkatt.trekkstatus == AVSLUTTET.name) LocalDate.now().minusDays(1).toString() else null
        val nyTrekkId = "${trekkFraSkatt.trekkid}${trekkalternativ.value}"

        val tssId = "kreditorIdTss"

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
            )

        return DokumentTilOppdrag(transaksjonsID, innrapporteringTrekk)
    }

    // Periode is expired if periodeFomDato < today. sats == 0.0 or there exists an overlapping period subsequent to it with sats=0.0
    private fun obsoleted(allePerioder: List<PeriodeTilOS>, it: PeriodeTilOS) =
        when {
            it.sats == 0.0 -> true
            it.isExpired() -> true
            allePerioder.any { periode ->
                it.periodeFomDato == periode.periodeFomDato &&
                    it.periodeTomDato == periode.periodeTomDato &&
                    it.id < periode.id &&
                    periode.sats == 0.0
            } -> true
            else -> false
        }
}
