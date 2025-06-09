package no.nav.sokos.utleggstrekk.service

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.util.resourceToString
import no.nav.sokos.utleggstrekk.utils.LocalDateSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.TSSId
import no.nav.sokos.utleggstrekk.utils.ZonedDateTimeSerializer

class TSSIdTest : FunSpec({
    val json = Json {
        prettyPrint = true
        isLenient = true
        explicitNulls = false
        serializersModule = SerializersModule {
            contextual(ZonedDateTimeSerializer)
            contextual(LocalDateTimeSerializer)
            contextual(LocalDateSerializer)
        }
    }


    test("hvis vi spør med korrekt ornr og konto skal vi få TSS id") {
        val skatt = TSSId.SKATT
        val trekkpalegg = json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
        val tssId = TSSId.getTSSId(trekkpalegg.first())

        skatt.orgnr shouldBe trekkpalegg.first().betalingsinformasjon.betalingsmottaker
        skatt.konto shouldBe trekkpalegg.first().betalingsinformasjon.kontonummer
        tssId shouldBe skatt.tssId
    }

    test("hvis vi spør med feil orgid skal vi få NotImplementedError exception") {
        val trekkpalegg = json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json")).first()
        val betalingsinfo = trekkpalegg.betalingsinformasjon.copy(betalingsmottaker = "123456789")
        val feiltrekk = trekkpalegg.copy(betalingsinformasjon = betalingsinfo)
        shouldThrowExactly<NotImplementedError> {
            TSSId.getTSSId(feiltrekk)
        }
    }

    test("hvis vi spør med feil konto skal vi få NotImplementedError exception") {
        val trekkpalegg = json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json")).first()
        val betalingsinfo = trekkpalegg.betalingsinformasjon.copy(kontonummer = "123456789")
        val feiltrekk = trekkpalegg.copy(betalingsinformasjon = betalingsinfo)
        val exception = shouldThrowExactly<NotImplementedError> {
            TSSId.getTSSId(feiltrekk)
        }
        println(exception)
    }

})
