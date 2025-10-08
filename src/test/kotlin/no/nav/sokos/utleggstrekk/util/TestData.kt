@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.util

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant
import kotlinx.datetime.toKotlinLocalDateTime

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode

object TestData {
    fun UtleggstrekkTable(
        sekvensnummer: Int,
        trekkIdSke: String,
        trekkversjon: Int,
        trekkstatus: Trekkstatus = AKTIV,
        status: UtleggstrekkStatus = MOTTATT,
    ) = UtleggstrekkTable(
        0L,
        null,
        sekvensnummer,
        saksnummer = "",
        trekkIdSke,
        trekkversjon,
        opprettetSke = LocalDateTime.now().toKotlinLocalDateTime(), // Bruk java-datetime.
        trekkpliktig = "",
        skyldner = "",
        trekkstatus = trekkstatus,
        kid = "",
        kontonummer = "",
        betalingsmottaker = "",
        corrid = UUID.randomUUID().toString(),
        status,
        kvitteringLOPM = null,
        kvitteringLOPP = null,
        tidspunktSendtOs = null,
        tidspunktSisteStatus = LocalDateTime.now().toKotlinLocalDateTime(), // TODO: ikke bruke KotlinLocalDateTime her.
        tidspunktOpprettet = LocalDateTime.now().toKotlinLocalDateTime(), // TODO: ikke bruke KotlinLocalDateTime her.
    )

    fun UtleggstrekkTable.trekkPeriode(
        sats: Double,
        trekkAlternativ: TrekkAlternativ,
        datoStart: String,
        datoSlutt: String,
    ) = TrekkPeriodeTable(
        0,
        sekvensnummer,
        trekkidSke,
        trekkversjon,
        datoStart,
        datoSlutt,
        sats,
        trekkAlternativ,
        LocalDateTime.now().toKotlinLocalDateTime(), // TODO: ikke bruke KotlinLocalDateTime her.
    )

    fun Trekkpaalegg(
        trekkId: String,
        sekvensnummer: Int,
        trekkversjon: Int,
        opprettet: LocalDateTime = LocalDateTime.now(),
        saksnummer: String = "saksnr1",
        trekkpliktig: String = "889640782",
        skyldner: String = "19628198007",
        trekkstatus: Trekkstatus = AKTIV,
        perioder: List<TrekkstorrelseForPeriode>,
        mottaker: Betalingsinformasjon =
            Betalingsinformasjon(
                betalingsmottaker = "971648198",
                kidnummer = "17654202404",
                kontonummer = "76940512057",
            ),
    ): Trekkpaalegg =
        Trekkpaalegg(
            trekkId,
            sekvensnummer,
            trekkversjon,
            opprettet.toInstant(ZoneOffset.UTC).toKotlinInstant(),
            saksnummer,
            trekkpliktig,
            skyldner,
            Trekkstatus.AKTIV,
            perioder,
            Betalingsinformasjon("mr.mottaker", "13812738912427", "6123101233424"),
        )
}
