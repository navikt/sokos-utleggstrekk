package no.nav.sokos.utleggstrekk.utils

import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg

enum class TSSId(val orgnr:String, val konto: String, val tssId: String) {

    SKATT("971648198", "76940512057", "80000423362")
    ;
    companion object {
        fun getTSSId(trekkpaalegg: Trekkpaalegg): String {
            return TSSId.entries.firstOrNull {
                trekkpaalegg.betalingsinformasjon.betalingsmottaker == it.orgnr && trekkpaalegg.betalingsinformasjon.kontonummer == it.konto
            }?.tssId
                ?: throw NotImplementedError(
                    "Kombinasjonen Orgnr=${trekkpaalegg.betalingsinformasjon.betalingsmottaker} og KOnto=${trekkpaalegg.betalingsinformasjon.kontonummer} gir ingen TSSID.",
                )
        }
        fun getTSSId(trekkpaalegg: UtleggstrekkTable): String {
            return TSSId.entries.firstOrNull {
                trekkpaalegg.betalingsmottaker == it.orgnr && trekkpaalegg.kontonummer == it.konto
            }?.tssId
                ?: throw NotImplementedError(
                    "Kombinasjonen Orgnr=${trekkpaalegg.betalingsmottaker} og Konto=${trekkpaalegg.kontonummer} gir ingen TSSID.",
                )
        }
    }
}

fun Trekkpaalegg.hentTSSId(): String = TSSId.getTSSId(this)

