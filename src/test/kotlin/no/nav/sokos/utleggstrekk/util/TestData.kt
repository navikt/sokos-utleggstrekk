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
    fun makeTrekkpaalegg(
        trekkId: String,
        sekvensnummer: Int,
        trekkversjon: Int,
        opprettet: String = "2024-06-16T13:33:05.672Z",
        saksnummer: String = "saksnr1",
        trekkpliktig: String = "889640782",
        skyldner: String = "19628198007",
        trekkstatus: Trekkstatus = AKTIV,
        perioder: List<TrekkstorrelseForPeriode> =
            listOf(
                TrekkstorrelseForPeriode(
                    "2026-02-02",
                    "2026-04-02",
                    trekkprosent = Trekkprosent(20.0),
                ),
            ),
        mottaker: Betalingsinformasjon =
            Betalingsinformasjon(
                betalingsmottaker = "971648199",
                kidnummer = "17654202404",
                kontonummer = "70213997155",
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
}