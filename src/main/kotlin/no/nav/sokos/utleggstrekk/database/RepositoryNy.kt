package no.nav.sokos.utleggstrekk.database

import kotliquery.Session
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.Feilmelding
import no.nav.sokos.utleggstrekk.database.model.INGEN_TREKK_ID_I_KVITTERING
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.database.model.Periode
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg

object RepositoryNy {
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
                    "trekkId" to trekkId,
                    "trekkversjon" to trekkversjon,
                ),
            ),
        ) { 1 } != null

    fun insertTrekkFraSkatt(trekkpaalegg: Trekkpaalegg, session: Session): Long? {
        val fraSkattId =
            session.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    INSERT INTO fraskatt(
                    trekkid,
                    sekvensnummer,
                    trekkversjon,
                    opprettet,
                    saksnummer,
                    trekkpliktig,
                    skyldner,
                    trekkstatus
                    )
                    
                    VALUES(:trekkid, :sekvensnummer, :trekkversjon, :opprettet, :saksnummer, :trekkpliktig, :skyldner, :trekkstatus)
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
                    INSERT INTO periode(
                    fraskatt_id,
                    dato_start, 
                    dato_slutt,
                    trekkbelop,
                    trekkprosent)
                    VALUES(:fraskattID, :startdato, :sluttDato, :trekkBelop, :trekkProsent)     
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
                INSERT INTO betalingsinformasjonfraskatt(
                fraskatt_id,
                betalingsmottaker,
                kidnummer,
                kontonummer
                )
                VALUES(:fraskattID, :betalingsmottaker, :kidnummer, :kontonummer)   
                """.trimIndent(),
                mapOf(
                    "fraskattID" to fraSkattId,
                    "betalingsmottaker" to trekkpaalegg.betalingsinformasjon.betalingsmottaker,
                    "kidnummer" to trekkpaalegg.betalingsinformasjon.kidnummer,
                    "kontonummer" to trekkpaalegg.betalingsinformasjon.kontonummer,
                ),
            ),
        )
        session.update(
            queryOf(
                """
                INSERT INTO fraskatt_status(fraskatt_id, status)
                VALUES(:fraskattID, :status)
                """.trimIndent(),
                mapOf(
                    "fraskattID" to fraSkattId,
                    "status" to MOTTATT.name,
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

    fun insertTransaksjonTilOs(dto: OSDto, session: Session) {
        session.update(
            queryOf(
                """
                INSERT INTO 
                transaksjon_os(
                    transaksjon_id, 
                    fraskatt_trekk_id, 
                    aksjonskode,
                    trekkalternativ,
                    transaksjon_status, 
                    kvittering_status 
                ) 
                VALUES(
                    :transaksjonsId, 
                    :fraskattId, 
                    :aksjonskode,
                    :trekkalternativ,
                    :status, 
                    :kvitteringStatus 
                    )
                """.trimIndent(),
                mapOf(
                    "transaksjonsId" to dto.transaksjonsID,
                    "fraskattId" to dto.fraSkattID,
                    "aksjonskode" to dto.aksjonskode.name,
                    "trekkalternativ" to dto.trekkAlternativ.name,
                    "status" to TransaksjonsStatus.IKKE_SENDT.name,
                    "kvitteringStatus" to KvitteringStatus.IKKE_MOTTATT.name,
                ),
            ),
        )
    }

    fun updateTransaksjonStatus(transaksjonId: String, transaksjonStatus: TransaksjonsStatus, session: Session) {
        session.update(
            queryOf(
                """
                UPDATE transaksjon_os 
                SET 
                transaksjon_status=:status,
                tidspunkt_siste_status=NOW() 
                WHERE transaksjon_id=:transaksjonsId
                """.trimIndent(),
                mapOf(
                    "status" to transaksjonStatus.name,
                    "transaksjonsId" to transaksjonId,
                ),
            ),
        )
    }

    // TODO: Rename?
    fun updateTransaksjon(
        transaksjonId: String,
        kvitteringStatus: KvitteringStatus,
        navTrekkId: String,
        session: Session,
    ) {
        session.update(
            queryOf(
                """
                UPDATE transaksjon_os 
                SET 
                kvittering_status=:kvitteringStatus,
                nav_trekk_id=:navTrekkId
                WHERE transaksjon_id=:transaksjonId
                """.trimIndent(),
                mapOf(
                    "kvitteringStatus" to kvitteringStatus.name,
                    "navTrekkId" to navTrekkId.ifEmpty { INGEN_TREKK_ID_I_KVITTERING },
                    "transaksjonId" to transaksjonId,
                ),
            ),
        )
    }

    fun getTransaksjonTilOs(transaksjonsId: String, session: Session): TransaksjonOS? =
        session.single(
            queryOf(
                """
                SELECT * FROM transaksjon_os WHERE transaksjon_id=:transaksjonsId
                """.trimIndent(),
                mapOf("transaksjonsId" to transaksjonsId),
            ),
        ) { row ->
            TransaksjonOS(row)
        }

    fun getTransaksjonerTilOsForTrekkID(trekkIdFraSkatt: String, session: Session): List<TransaksjonOS> =
        session.list(
            queryOf(
                """
                SELECT * FROM transaksjon_os WHERE fraskatt_trekk_id=:trekkIdFraSkatt
                """.trimIndent(),
                mapOf("trekkIdFraSkatt" to trekkIdFraSkatt),
            ),
        ) { row ->
            TransaksjonOS(row)
        }

    fun getAllTransaksjonerTilOs(session: Session): List<TransaksjonOS> =
        session.list(
            queryOf(
                """
                SELECT * FROM transaksjon_os
                """.trimIndent(),
            ),
        ) { row ->
            TransaksjonOS(row)
        }

    fun getTrekkFraSkattMedStatus(status: SkattTrekkStatus, session: Session): List<TrekkFraSkatt> =
        session.list(
            queryOf(
                "SELECT * FROM fraskatt f JOIN fraskatt_status s ON f.id = s.fraskatt_id WHERE s.status=:status ORDER BY f.sekvensnummer ASC",
                mapOf("status" to status.name),
            ),
        ) { row ->
            TrekkFraSkatt(row)
        }

    fun setStatus(fraSkatt: TrekkFraSkatt, status: SkattTrekkStatus, session: Session) {
        session.update(
            queryOf(
                "UPDATE fraskatt_status SET status = :status, tidspunkt_satt = NOW() WHERE id = :id",
                mapOf("id" to fraSkatt.id, "status" to status.name),
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

    fun getTrekkFraSkatt(id: String, session: Session): TrekkFraSkatt? =
        session.single(
            queryOf(
                """SELECT * FROM fraskatt WHERE trekkid=:id""".trimIndent(),
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
            queryOf("""SELECT sekvensnummer FROM fraskatt ORDER BY sekvensnummer DESC LIMIT 1"""),
        ) { row -> row.intOrNull(1) } ?: 0
}