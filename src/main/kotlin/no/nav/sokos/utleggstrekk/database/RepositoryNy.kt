package no.nav.sokos.utleggstrekk.database

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Session
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.Feilmelding
import no.nav.sokos.utleggstrekk.database.model.INGEN_TREKK_ID_I_KVITTERING
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeTilOS
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode

class RepositoryNy(private val dataSource: HikariDataSource) {
    fun doesTrekkExist(trekkId: String, trekkversjon: Int, session: Session): Boolean =
        session.single(
            queryOf(
                """
                SELECT 1
                FROM fraskatt
                WHERE trekkid = :trekkId
                  AND trekkversjon = :trekkversjon
                """.trimIndent(),
                mapOf(
                    "trekkId" to trekkId,
                    "trekkversjon" to trekkversjon,
                ),
            ),
        ) { 1 } != null

    fun insertPeriodeFraSkatt(
        fraSkattId: Long?,
        trekkIdSke: String,
        periode: TrekkstorrelseForPeriode,
        session: Session,
    ) {
        session.update(
            queryOf(
                """
                INSERT INTO periode(
                fraskatt_id,
                trekk_id_ske,
                dato_start, 
                dato_slutt,
                trekkbelop,
                trekkprosent)
                VALUES(:fraskattID, :trekkIDSke, :startdato, :sluttDato, :trekkBelop, :trekkProsent)     
                """.trimIndent(),
                mapOf(
                    "fraskattID" to fraSkattId,
                    "trekkIDSke" to trekkIdSke,
                    "startdato" to periode.startdato,
                    "sluttDato" to periode.sluttdato,
                    "trekkBelop" to periode.trekkbeloep?.trekkbeloep,
                    "trekkProsent" to periode.trekkprosent?.trekkprosent,
                ),
            ),
        )
    }

    fun insertFraSkattStatus(fraSkattId: Long?, status: SkattTrekkStatus, session: Session) {
        session.update(
            queryOf(
                """
                INSERT INTO fraskatt_status(fraskatt_id, status)
                VALUES(:fraskattID, :status)
                """.trimIndent(),
                mapOf(
                    "fraskattID" to fraSkattId,
                    "status" to status.name,
                ),
            ),
        )
    }

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
            insertPeriodeFraSkatt(fraSkattId, trekkpaalegg.trekkid, periode, session)
        }
        insertBetalingsinformasjonFraSkatt(fraSkattId, trekkpaalegg.betalingsinformasjon, session)
        insertFraSkattStatus(fraSkattId, SkattTrekkStatus.MOTTATT, session)

        return fraSkattId
    }

    fun insertBetalingsinformasjonFraSkatt(fraSkattId: Long?, betalingsInformasjon: Betalingsinformasjon, session: Session) {
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
                    "betalingsmottaker" to betalingsInformasjon.betalingsmottaker,
                    "kidnummer" to betalingsInformasjon.kidnummer,
                    "kontonummer" to betalingsInformasjon.kontonummer,
                ),
            ),
        )
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

    private object TransaksjonOsTable {
        const val TRANSAKSJONS_ID_PARAM = "transaksjonsId"
        const val TRANSAKSJON_STATUS_PARAM = "transaksjonStatus"
        const val TREKK_ID_SKE_PARAM = "trekkIdSke"
        const val KVITTERING_STATUS_PARAM = "kvitteringStatus"
        const val AKSJONSKODE_PARAM = "aksjonskode"
        const val KREDITOR_ID_TSS_PARAM = "kreditorIdTss"
        const val KREDITOR_TREKK_ID_PARAM = "kreditorTrekkId"
        const val KREDITORSREF_PARAM = "kreditorsref"
        const val DEBITOR_ID_PARAM = "debitorId"
        const val TREKKALTERNATIV_PARAM = "trekkalternativ"
        const val TREKK_TYPE_PARAM = "trekkType"
        const val KID_PARAM = "kid"
        const val KILDE_PARAM = "kilde"
        const val PRIORITET_FOM_DATO_PARAM = "prioritetFomDato"
        const val GYLDIG_TOM_DATO_PARAM = "gyldigTomDato"
        const val TRANSAKSJON_ID_COLUMN = "transaksjon_id"
        const val TRANSAKSJON_STATUS_COLUMN = "transaksjon_status"
        const val TREKK_ID_SKE_COLUMN = "trekk_id_ske"
        const val KVITTERING_STATUS_COLUMN = "kvittering_status"
        const val AKSJONSKODE_COLUMN = "aksjonskode"
        const val KREDITOR_ID_TSS_COLUMN = "kreditor_id_tss"
        const val KREDITOR_TREKK_ID_COLUMN = "kreditor_trekk_id"
        const val KREDITORSREF_COLUMN = "kreditorsref"
        const val DEBITOR_ID_COLUMN = "debitor_id"
        const val TREKK_ALTERNATIV_COLUMN = "trekk_alternativ"
        const val TREKK_TYPE_COLUMN = "trekk_type"
        const val KID_COLUMN = "kid"
        const val KILDE_COLUMN = "kilde"
        const val PRIORITET_FOM_DATO_COLUMN = "prioritet_fom_dato"
        const val GYLDIG_TOM_DATO_COLUMN = "gyldig_tom_dato"
    }

    fun insertTransaksjonTilOs(dto: OSDto, session: Session) {
        session.update(
            queryOf(
                """
                INSERT INTO 
                transaksjon_os(
                    ${TransaksjonOsTable.TRANSAKSJON_ID_COLUMN}, 
                     ${TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN}, 
                     ${TransaksjonOsTable.TREKK_ID_SKE_COLUMN}, 
                     ${TransaksjonOsTable.KVITTERING_STATUS_COLUMN}, 
                     ${TransaksjonOsTable.AKSJONSKODE_COLUMN}, 
                     ${TransaksjonOsTable.KREDITOR_ID_TSS_COLUMN}, 
                     ${TransaksjonOsTable.KREDITOR_TREKK_ID_COLUMN}, 
                     ${TransaksjonOsTable.KREDITORSREF_COLUMN}, 
                     ${TransaksjonOsTable.DEBITOR_ID_COLUMN}, 
                     ${TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN}, 
                     ${TransaksjonOsTable.TREKK_TYPE_COLUMN}, 
                     ${TransaksjonOsTable.KID_COLUMN},
                     ${TransaksjonOsTable.KILDE_COLUMN},
                     ${TransaksjonOsTable.PRIORITET_FOM_DATO_COLUMN},
                     ${TransaksjonOsTable.GYLDIG_TOM_DATO_COLUMN}
                ) 
                VALUES(
                    :${TransaksjonOsTable.TRANSAKSJONS_ID_PARAM},
                    :${TransaksjonOsTable.TRANSAKSJON_STATUS_PARAM},
                    :${TransaksjonOsTable.TREKK_ID_SKE_PARAM},
                    :${TransaksjonOsTable.KVITTERING_STATUS_PARAM},
                    :${TransaksjonOsTable.AKSJONSKODE_PARAM},
                    :${TransaksjonOsTable.KREDITOR_ID_TSS_PARAM},
                    :${TransaksjonOsTable.KREDITOR_TREKK_ID_PARAM},
                    :${TransaksjonOsTable.KREDITORSREF_PARAM},
                    :${TransaksjonOsTable.DEBITOR_ID_PARAM},
                    :${TransaksjonOsTable.TREKKALTERNATIV_PARAM},
                    :${TransaksjonOsTable.TREKK_TYPE_PARAM},
                    :${TransaksjonOsTable.KID_PARAM},
                    :${TransaksjonOsTable.KILDE_PARAM},
                    :${TransaksjonOsTable.PRIORITET_FOM_DATO_PARAM},
                    :${TransaksjonOsTable.GYLDIG_TOM_DATO_PARAM}
                    )
                """.trimIndent(),
                mapOf(
                    TransaksjonOsTable.TRANSAKSJONS_ID_PARAM to dto.transaksjonID,
                    TransaksjonOsTable.TRANSAKSJON_STATUS_PARAM to TransaksjonsStatus.IKKE_SENDT.name,
                    TransaksjonOsTable.TREKK_ID_SKE_PARAM to dto.trekkIDSke,
                    TransaksjonOsTable.KVITTERING_STATUS_PARAM to KvitteringStatus.IKKE_MOTTATT.name,
                    TransaksjonOsTable.AKSJONSKODE_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.aksjonskode.name,
                    TransaksjonOsTable.KREDITOR_ID_TSS_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.kreditorIdTss,
                    TransaksjonOsTable.KREDITOR_TREKK_ID_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.kreditorTrekkId,
                    TransaksjonOsTable.KREDITORSREF_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.kreditorsRef,
                    TransaksjonOsTable.DEBITOR_ID_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.debitorId,
                    TransaksjonOsTable.TREKKALTERNATIV_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.kodeTrekkAlternativ.name,
                    TransaksjonOsTable.TREKK_TYPE_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.kodeTrekktype,
                    TransaksjonOsTable.KID_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.kid,
                    TransaksjonOsTable.KILDE_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.kilde,
                    TransaksjonOsTable.PRIORITET_FOM_DATO_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.prioritetFomDato,
                    TransaksjonOsTable.GYLDIG_TOM_DATO_PARAM to dto.dokumentTilOppdrag.innrapporteringTrekk.gyldigTomDato,
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
            val transaksjonId = row.long("id")
            val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

            TransaksjonOS(row, perioderTilOS)
        }

    fun getTransaksjonerTilOsForTrekkID(trekkIdSke: String, session: Session): List<TransaksjonOS> =
        session.list(
            queryOf(
                """
                SELECT * FROM transaksjon_os WHERE trekk_id_ske=:trekkIdSke
                """.trimIndent(),
                mapOf("trekkIdSke" to trekkIdSke),
            ),
        ) { row ->
            val transaksjonId = row.long("id")
            val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

            TransaksjonOS(row, perioderTilOS)
        }

    fun getAllTransaksjonerTilOs(session: Session): List<TransaksjonOS> =
        session.list(
            queryOf(
                """
                SELECT * FROM transaksjon_os
                """.trimIndent(),
            ),
        ) { row ->
            val transaksjonId = row.long("id")
            val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

            TransaksjonOS(row, perioderTilOS)
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

    fun getTrekkFraSkatt(id: String, session: Session): List<TrekkFraSkatt> =
        session.list(
            queryOf(
                """SELECT * FROM fraskatt WHERE trekkid=:id""".trimIndent(),
                mapOf("id" to id),
            ),
        ) { row -> TrekkFraSkatt(row) }

    fun getAllePerioderForTrekkId(trekkIdSke: String, session: Session): List<PeriodeFraSkatt> =
        session.list(
            queryOf(
                """SELECT * FROM periode WHERE trekk_id_ske=:trekkIdSke""".trimIndent(),
                mapOf("trekkIdSke" to trekkIdSke),
            ),
        ) { row -> PeriodeFraSkatt(row) }

    fun getPerioderForTrekkVersjon(
        fraSkattId: Long,
        sekvensnummer: Int,
        trekkversjon: Int,
        session: Session,
    ): List<PeriodeFraSkatt> =
        session.list(
            queryOf(
                """
                    SELECT p.* FROM periode p
                JOIN fraskatt f ON p.fraskatt_id = f.id
                WHERE f.sekvensnummer = :sekvensnummer
                 AND f.trekkversjon = :trekkversjon
                   
                """.trimIndent(),
                mapOf(
                    "sekvensnummer" to sekvensnummer,
                    "fraSkattId" to fraSkattId,
                    "trekkversjon" to trekkversjon,
                ),
            ),
        ) { row -> PeriodeFraSkatt(row) }

    fun getPerioderForTrekk(trekkFraSkatt: TrekkFraSkatt, session: Session): List<PeriodeFraSkatt> =
        getPerioderForTrekkVersjon(trekkFraSkatt.id, trekkFraSkatt.sekvensnummer, trekkFraSkatt.trekkversjon, session)

    fun getTrekkAlternativOS(trekkIdSke: String, session: Session): List<TrekkAlternativ> =
        session.list(
            queryOf(
                """SELECT 
                    DISTINCT ${TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN}
                     FROM transaksjon_os 
                     WHERE ${TransaksjonOsTable.TREKK_ID_SKE_COLUMN}=:${TransaksjonOsTable.TREKK_ID_SKE_PARAM}
                      AND ${TransaksjonOsTable.KVITTERING_STATUS_COLUMN} = '${KvitteringStatus.OK}' 
                      OR ${TransaksjonOsTable.KVITTERING_STATUS_COLUMN}  = '${KvitteringStatus.IKKE_MOTTATT.name}'
                """.trimMargin(),
                mapOf(TransaksjonOsTable.TREKK_ID_SKE_PARAM to trekkIdSke),
            ),
        ) { row -> TrekkAlternativ.valueOf(row.string(TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN).uppercase()) }

    fun insertPeriodeTilOs(transaksjonOSId: Int, periodeTilOs: PeriodeTilOS, session: Session) {
        session.update(
            queryOf(
                """
                                INSERT INTO periode_til_os (transaksjon_os_id, sats, periode_fom_dato, periode_tom_dato)
                                VALUES (:transaksjon_os_id, :sats, :periodeFomDato, :periodeTomDato)
                                """,
                mapOf(
                    "transaksjon_os_id" to transaksjonOSId,
                    "sats" to periodeTilOs.sats,
                    "periodeFomDato" to periodeTilOs.periodeFomDato,
                    "periodeTomDato" to periodeTilOs.periodeTomDato,
                ),
            ),
        )
    }

    fun getPerioderTilOs(trekkIdSke: String, alternativ: TrekkAlternativ, session: Session): List<PeriodeTilOS> =
        session.list(
            queryOf(
                """SELECT * FROM
                     periode_til_os p 
                    JOIN transaksjon_os t ON p.transaksjon_os_id = t.id 
                    WHERE 
                    trekk_id_ske=:trekkIdSke 
                    AND t.trekk_alternativ=:trekkAlternativ 
                    AND t.kvittering_status in ('${KvitteringStatus.IKKE_MOTTATT.name}', '${KvitteringStatus.OK.name}')
                    """,
                mapOf(
                    "trekkIdSke" to trekkIdSke,
                    "trekkAlternativ" to alternativ.name,
                ),
            ),
        ) { row -> PeriodeTilOS(row) }

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

// TODO: Bruke fraskatt_status? Må også oppdatere hvordan ostransaksjon funker
    fun getTrekkSomIkkeErSendt(session: Session): List<TrekkFraSkatt> =
        session.list(
            queryOf(
                """
                SELECT f.* FROM fraskatt f
                LEFT JOIN transaksjon_os t ON t.trekk_id_ske = f.trekkid
                WHERE t.transaksjon_status IS NULL OR t.transaksjon_status = 'IKKE_SENDT'
                """.trimIndent(),
            ),
        ) { row -> TrekkFraSkatt(row) }

    // fun updatePeriodeStatus(periode: PeriodeTilOS, status: PeriodeStatus, session: kotliquery.TransactionalSession) {
    // }

    fun insertTrekkForOS(nyPeriode: PeriodeTilOS, session: Session) {}

    fun getTransaksjonerTilOsSomIkkeErSendt(session: Session) {
        session.list(
            queryOf(
                """
                select * from transaksjon_os where transaksjon_status is null or transaksjon_status = 'IKKE_SENDT'
                """.trimIndent(),
            ),
        ) { row ->
            val transaksjonId = row.long("id")
            val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

            TransaksjonOS(row, perioderTilOS)
        }
    }

    private fun getPerioderForTransaksjon(transaksjonOSId: Long, session: Session): List<PeriodeTilOS> {
        val perioderTilOS = mutableListOf<PeriodeTilOS>()
        session.list(
            queryOf(
                """
                select * from periode_til_os where transaksjon_os_id = :transaksjonOSId
                """.trimIndent(),
                mapOf("transaksjonOSId" to transaksjonOSId),
            ),
        ) { periodeRow ->
            perioderTilOS.add(PeriodeTilOS(periodeRow))
        }
        return perioderTilOS.toList()
    }

    fun getOsAlternativForTrekk(trekk: TrekkFraSkatt, session: Session): Set<TrekkAlternativ> =
        session
            .list(
                queryOf(
                    """
                    SELECT DISTINCT trekk_alternativ from transaksjon_os WHERE trekk_id_ske=:trekkIdSek AND
                        transaksjonStatus NOT IN ('FEIL', 'UKJENT')
                    """.trimIndent(),
                    mapOf("trekkIdSek" to trekk),
                ),
            ) { row ->
                TrekkAlternativ.valueOf(row.string("trekk_alternativ"))
            }.toSet()
}