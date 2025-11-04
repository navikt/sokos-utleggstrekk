@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.util

import kotlin.time.ExperimentalTime

import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkprosent
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode

object TestData {
     /*fun utleggstrekkTable(
        sekvensnummer: Int,
        trekkIdSke: String,
        trekkversjon: Int,
        trekkstatus: Trekkstatus = AKTIV,
        status: UtleggstrekkStatus = MOTTATT,
    ) = UtleggstrekkTable(
        utleggstrekkTableId = 0L,
        trekkidNavLOPP = null,
        trekkidNavLOPM = null,
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
        trekkPeriodeTableId = 0,
        sekvensnummer,
        trekkidSke,
        trekkversjon,
        datoStart,
        datoSlutt,
        sats,
        trekkAlternativ,
        LocalDateTime.now().toKotlinLocalDateTime(), // TODO: ikke bruke KotlinLocalDateTime her.
    )*/

    fun makeTrekkpaalegg(
        trekkId: String,
        sekvensnummer: Int,
        trekkversjon: Int,
        opprettet: String = "2024-06-16T13:33:05.672Z",
        saksnummer: String = "saksnr1",
        trekkpliktig: String = "889640782",
        skyldner: String = "19628198007",
        trekkstatus: Trekkstatus = AKTIV,
        perioder: List<TrekkstorrelseForPeriode> = trekkPeriode,
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
            opprettet,
            saksnummer,
            trekkpliktig,
            skyldner,
            trekkstatus,
            perioder,
            mottaker,
        )

    private val trekkPeriode =
        listOf(
            TrekkstorrelseForPeriode(
                "2026-02-02",
                "2026-04-02",
                trekkprosent = Trekkprosent(20.0),
            ),
        )
}