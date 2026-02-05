package no.nav.sokos.utleggstrekk.service

import java.time.LocalDate
import java.util.UUID

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeTilOS
import no.nav.sokos.utleggstrekk.database.model.PerioderTilOS
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.database.model.mapNewFomTom
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode.ENDR
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode.NY
import no.nav.sokos.utleggstrekk.domene.nav.DokumentTilOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPM
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPP
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AVSLUTTET
import no.nav.sokos.utleggstrekk.utils.SyntetiskId.konverterTrekkId
import no.nav.sokos.utleggstrekk.utils.TssIdResolver

const val KODE_TREKKTYPE = "TRK1"
const val KILDE = "SOKOSUTLEGG"

private val logger = KotlinLogging.logger { }

class BehandleTrekkService(private val repository: Repository = Repository(PostgresDataSource.dataSource)) {
    fun behandleTrekk() =
        repository.getTrekkIdTilTrekkSomSkalBehandles().forEach { trekkId ->
            repository.withTransaction { session ->
                try {
                    val trekk = repository.getTrekkFraSkatt(trekkId, session)
                    val status = repository.getSkattTrekkStatus(trekk.id, session)

                    if (status == SkattTrekkStatus.REPETERES) {
                        if (repository.getNyesteTrekkVersjon(trekk.trekkid, session).trekkversjon != trekk.trekkversjon) {
                            repository.updateTrekkFraSkattStatus(trekk.id, SkattTrekkStatus.HOPPET_OVER)
                            return@withTransaction
                        }
                    }
                    val documents = lagTrekkDokument(trekk, session)
                    documents.forEach { document ->
                        val documentJson = jsonConfig.encodeToString<TrekkTilOppdrag>(document)
                        val dto =
                            OSDto(
                                document.dokument.transaksjonsId,
                                trekk.trekkid,
                                trekkversjon = trekk.trekkversjon,
                                document.dokument.innrapporteringTrekk,
                                documentJson,
                            )
                        repository.insertTransaksjonTilOs(dto, session)
                        repository.updateTrekkFraSkattStatus(trekk.id, SkattTrekkStatus.BEHANDLET, session)
                    }
                } catch (e: Exception) {
                    // Todo: Logg exception i team log
                    logger.error("Feil under prossessering av trekk med trekkid=$trekkId")
                    repository.updateTrekkFraSkattStatus(trekkId, SkattTrekkStatus.AVVIST)
                }
            }
        }

    private fun lagTrekkDokument(trekk: TrekkFraSkatt, session: TransactionalSession): List<TrekkTilOppdrag> {
        // Vi trenger å vite om trekk(ene) er kjent for OS
        val kjenteAlternativ = repository.getOsAlternativForTrekk(trekk, session)
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
        val trekkPerioder = repository.getPerioderForTrekk(trekkFraSkatt).mapNewFomTom()
        // Alternativene som finnes i dette trekket + alternativ fra dokumenter vi har sendt eller skal sende til OS
        val alternativ =
            buildSet {
                addAll(trekkPerioder.map { it.trekkAlternativ() }.distinct())
                addAll(repository.getOsAlternativForTrekk(trekkFraSkatt, session))
            }

        // Vi henter kjente osPerioder for å se etter endringer. Bare perioder som er fortsatt gyldige og som har en sats er relevante.
        val osPerioder =
            alternativ.associateWith { alternativ ->
                val allePerioder = repository.getPerioderTilOs(trekkFraSkatt.trekkid, alternativ)
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
        // TODO: Legge inn  sjekk på at minst én av dem ikke er tom HVIS trekket ikke er type avsluttet. Logg feil hvis sjekk feiler
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
    ): TrekkTilOppdrag {
        // Må finne: TSSID, Aksjonskode, Trekkalternativ, Perioder
        // kreditorTrekkId skal ha M eller P på slutten

        // Må hente betalingsinformasjonen til trekket for å finne tssid og kid

        // TODO: Lage custom exception
        val betalingsinformasjon: BetalingsinformasjonFraSkatt =
            repository.getBetalingsinformasjonForTrekk(trekkFraSkatt.id) ?: throw Exception("Betalingsinformasjon er null for trekkId=${trekkFraSkatt.id}")

        val perioder = Perioder(perioderTilOS.map { it.asPeriode() }).takeUnless { it.periode.isEmpty() }

        val transaksjonsID = UUID.randomUUID().toString()
        val gyldigTomDato = if (trekkFraSkatt.trekkstatus == AVSLUTTET.name) LocalDate.now().toString() else null
        val nyTrekkId = konverterTrekkId(trekkFraSkatt.trekkid, trekkalternativ)
        val kreditorsRef = if (trekkFraSkatt.saksnummer.length > 30) trekkFraSkatt.saksnummer.substring(0, 30) else trekkFraSkatt.saksnummer

        val tssId = TssIdResolver.resolve(betalingsinformasjon)

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
                kreditorsRef = kreditorsRef,
            )

        return TrekkTilOppdrag(DokumentTilOppdrag(transaksjonsID, innrapporteringTrekk))
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