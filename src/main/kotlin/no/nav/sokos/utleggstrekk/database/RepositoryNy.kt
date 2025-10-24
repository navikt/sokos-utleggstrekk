package no.nav.sokos.utleggstrekk.database

import kotliquery.Session
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.Feilmelding
import no.nav.sokos.utleggstrekk.database.model.Periode
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg

class RepositoryNy {
    fun doesTrekkExist(
        trekkId: String,
        sekvensnummer: Int,
        trekkversjon: Int,
        session: Session,
    ): Boolean =
        session.single(
            queryOf(
                """
                SELECT 1
                FROM fraskatt
                WHERE sekvensnummer = :sekvensnummer
                  AND trekkid = :trekkId
                  AND trekkversjon = :trekkversjon
                
                """.trimIndent(),
                mapOf(
                    "sekvensnummer" to sekvensnummer,
                    "trekkid" to trekkId,
                    "trekkversjon" to trekkversjon,
                ),
            ),
        ) { 1 } != null

    fun insertTrekkFraSkatt(trekkpaalegg: Trekkpaalegg, session: Session): Long? {
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

        return fraSkattId
    }

    fun insertFeilmeldingFraOS(kvittering: KvitteringFraOppdrag, session: Session) {
        session.update(
            queryOf(
                """
                insert into feilmelding (
                    kreditor_trekk_id,
                    transaksjons_id,
                    trekkalternativ,
                    feilkode,
                    beskrivelse
                ) values (
                    :kreditorTrekkId,
                    :transaksjonsId,
                    :kodeTrekkAlternativ,
                    :kodeMelding,
                    :beskrivelse
                )
                """.trimIndent(),
                mapOf(
                    "kreditorTrekkId" to kvittering.dokument.innrapporteringTrekk.kreditorTrekkId,
                    "transaksjonsId" to kvittering.dokument.transaksjonsId,
                    "kodeTrekkAlternativ" to kvittering.dokument.innrapporteringTrekk.kodeTrekkAlternativ.name,
                    "kodeMelding" to kvittering.mmel?.kodeMelding,
                    "beskrivelse" to kvittering.mmel?.beskrMelding,
                ),
            ),
        )
    }

    fun getFeilmeldingerFraOS(transaksjonsId: String, session: Session): Feilmelding? =
        session.single(
            queryOf(
                "SELECT * FROM feilmelding WHERE transaksjons_id=:transaksjonsId",
                mapOf("transaksjonsId" to transaksjonsId),
            ),
        ) { row -> Feilmelding(row) }

    fun getAllTrekkFraSkatt(session: Session): List<TrekkFraSkatt> =
        session.list(
            queryOf(
                """SELECT * FROM fraskatt""".trimIndent(),
            ),
        ) { row -> TrekkFraSkatt(row) }

    fun getTrekkFraSkatt(id: Long, session: Session): TrekkFraSkatt? =
        session.single(
            queryOf(
                """SELECT * FROM fraskatt WHERE id=:id""".trimIndent(),
                mapOf("id" to id),
            ),
        ) { row -> TrekkFraSkatt(row) }

    fun getPerioderForTrekk(id: Long, session: Session): List<Periode> =
        session.list(
            queryOf(
                """SELECT * FROM periode WHERE fraskatt_id=:id""".trimIndent(),
                mapOf("id" to id),
            ),
        ) { row -> Periode(row) }

    fun getBetalingsinformasjonForTrekk(id: Long, session: Session): BetalingsinformasjonFraSkatt? =
        session.single(
            queryOf(
                """SELECT * FROM betalingsinformasjonfraskatt WHERE fraskatt_id=:id""".trimIndent(),
                mapOf("id" to id),
            ),
        ) { row -> BetalingsinformasjonFraSkatt(row) }

    fun getLastSekvensnummer(session: Session): Int =
        session.single(
            queryOf("""SELECT MAX(sekvensnummer) FROM fraskatt"""),
        ) { row -> row.intOrNull(1) } ?: 0
}