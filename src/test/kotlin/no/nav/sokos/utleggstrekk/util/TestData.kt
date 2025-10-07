package no.nav.sokos.utleggstrekk.util

import java.time.LocalDateTime
import java.util.UUID

import kotlinx.datetime.toKotlinLocalDateTime

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV

// https://skatteetaten.github.io/api-dokumentasjon/api/trekkpaalegg?tab=Eksempler
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
}
