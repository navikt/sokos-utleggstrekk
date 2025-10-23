package no.nav.sokos.utleggstrekk.database

import kotlin.time.ExperimentalTime

import kotliquery.Session
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg

class RepositoryNy {
    @OptIn(ExperimentalTime::class)
    fun insertTrekkFraSkatt(trekkpaalegg: Trekkpaalegg, session: Session) {
        val fraSkattId =
            session.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    insert into fraskatt(
                    trekkid,
                    sekvensnummer,
                    trekkversjon,
                    opprettet,
                    saksnummer,
                    trekkpliktig,
                    skyldner,
                    trekkstatus
                    )
                    
                    values(:trekkid, :sekvensnummer, :trekkversjon, :opprettet, :saksnummer, :trekkpliktig, :skyldner, :trekkstatus)
                    """.trimIndent(),
                    mapOf(
                        "trekkid" to trekkpaalegg.trekkid,
                        "sekvensnummer" to trekkpaalegg.sekvensnummer,
                        "trekkversjon" to trekkpaalegg.trekkversjon,
                        "opprettet" to trekkpaalegg.opprettet,
                        "saksnummer" to trekkpaalegg.saksnummer,
                        "trekkpliktig" to trekkpaalegg.trekkpliktig,
                        "skyldner" to trekkpaalegg.skyldner,
                        "trekkstatus" to trekkpaalegg.trekkstatus.name,
                    ),
                ),
            )
        trekkpaalegg.trekkstoerrelseForPeriode.forEach { periode ->
            session.update(
                queryOf(
                    """
                    insert into periode(
                    fraskatt_id,
                    dato_start, 
                    dato_slutt,
                    trekkbelop,
                    trekkprosent)
                    values(:fraskattID, :startdato, :sluttDato, :trekkBelop, :trekkProsent)     
                    """.trimIndent(),
                    mapOf(
                        "fraskattID" to fraSkattId,
                        "startdato" to periode.startdato,
                        "sluttDato" to periode.sluttdato,
                        "trekkBelop" to periode.trekkbeloep?.trekkbeloep,
                        "trekkProsent" to periode.trekkprosent?.trekkprosent,
                    ),
                ),
            )
        }
        session.update(
            queryOf(
                """
                insert into betalingsinformasjonfraskatt(
                fraskatt_id,
                betalingsmottaker,
                kidnummer,
                kontonummer
                )
                values(:fraskattID, :betalingsmottaker, :kidnummer, :kontonummer)   
                """.trimIndent(),
                mapOf(
                    "fraskattID" to fraSkattId,
                    "betalingsmottaker" to trekkpaalegg.betalingsinformasjon.betalingsmottaker,
                    "kidnummer" to trekkpaalegg.betalingsinformasjon.kidnummer,
                    "kontonummer" to trekkpaalegg.betalingsinformasjon.kontonummer,
                ),
            ),
        )
    }
}