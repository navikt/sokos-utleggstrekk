package no.nav.sokos.utleggstrekk.database

import kotliquery.Session
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.Feilmelding
import no.nav.sokos.utleggstrekk.database.model.INGEN_TREKK_ID_I_KVITTERING
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeStatus
import no.nav.sokos.utleggstrekk.database.model.PeriodeTilOS
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
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
                    trekk_id_ske,
                    dato_start, 
                    dato_slutt,
                    trekkbelop,
                    trekkprosent)
                    VALUES(:fraskattID, :trekkIDSke, :startdato, :sluttDato, :trekkBelop, :trekkProsent)     
                    """.trimIndent(),
                    mapOf(
                        "fraskattID" to fraSkattId,
                        "trekkIDSke" to trekkpaalegg.trekkid,
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
                    id,
                    nav_trekk_id,
                    transaksjon_id, 
                    transaksjon_status, 
                    trekk_id_ske, 
                    kvittering_status,
                    tidspunkt_sendt,
                    tidspunkt_siste_status,
                    aksjonskode,
                    krediror_id_tss,
                    kreditor_trekk_id,
                    kreditorsref,
                    debitor_id,
                    trekk_alternativ,
                    trekk_type,
                    kid,
                    kilde,
                    saldo,
                    prioritet_fom_dato,
                    gyldig_tom_dato,
                ) 
                VALUES(
                    :id,
                    :navTrekkId,
                    :transaksjonsId, 
                    :transaksjonStatus,
                    :trekkIdSke,
                    :kvitteringStatus 
                    :tidspunktSendt,
                    :tidspunktSisteStatus,
                    :aksjonskode,
                    :kreditorIdTss,
                    :kreditorTrekkId,
                    :kreditorsef,
                    :debitorId,
                    :trekkalternativ,
                    :trekkType,
                    :kid,
                    :kilde,
                    :saldo,
                    :prioritetFomDato,
                    :gyldigTomDato,
                    )
                """.trimIndent(),
                mapOf(
                    "transaksjonsId" to dto.transaksjonsID,
                    "transaksjonStatus" to TransaksjonsStatus.IKKE_SENDT.name,
                    "trekkIdSke" to dto.trekkIDSke,
                    "kvitteringStatus" to KvitteringStatus.IKKE_MOTTATT.name,
                    "aksjonskode" to dto.dokumentTilOppdrag.innrapporteringTrekk.aksjonskode,
                    "kreditorIdTss" to dto.dokumentTilOppdrag.innrapporteringTrekk.kreditorIdTss,
                    "kreditorTrekkId" to dto.dokumentTilOppdrag.innrapporteringTrekk.kreditorTrekkId,
                    "kreditorsref" to dto.dokumentTilOppdrag.innrapporteringTrekk.kreditorsRef,
                    "debitorId" to dto.dokumentTilOppdrag.innrapporteringTrekk.debitorId,
                    "trekkalternativ" to dto.dokumentTilOppdrag.innrapporteringTrekk.kodeTrekkAlternativ.name,
                    "trekkType" to dto.dokumentTilOppdrag.innrapporteringTrekk.kodeTrekktype,
                    "kid" to dto.dokumentTilOppdrag.innrapporteringTrekk.kid,
                    "kilde" to dto.dokumentTilOppdrag.innrapporteringTrekk.kilde,
                    "saldo" to dto.dokumentTilOppdrag.innrapporteringTrekk.saldo,
                    "prioritetFomDato" to dto.dokumentTilOppdrag.innrapporteringTrekk.prioritetFomDato,
                    "gyldigTomDato" to dto.dokumentTilOppdrag.innrapporteringTrekk.gyldigTomDato,
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
            val transaksjonId = row.string("transaksjon_id")
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
            val transaksjonId = row.string("transaksjon_id")
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
            val transaksjonId = row.string("transaksjon_id")
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

    fun getTrekkFraSkatt(id: String, session: Session): TrekkFraSkatt? =
        session.single(
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

    fun getTrekkAlternativOS(trekkIdSke: String, session: Session): List<TrekkAlternativ> =
        session.list(
            queryOf(
                """SELECT DISTINCT trekkalternativ FROM transaksjon_os WHERE trekkid_ske=:trekkIdSke AND
                    kvittering_status = 'OK' OR kvittering_status = 'IKKE_MOTTATT
                """.trimMargin(),
                mapOf("trekkIdSke" to trekkIdSke),
            ),
        ) { row -> TrekkAlternativ.valueOf(row.string("trekkalternativ").uppercase()) }

    fun getPerioderTilOs(trekkIdSke: String, session: Session): List<PeriodeTilOS> =
        session.list(
            queryOf(
                """SELECT * FROM periode_til_os p JOIN transaksjon_os t ON p.transaksjons_os_id = t.id 
                    WHERE trekkid_ske=:trekkIdSke AND t.kvittering_status = 'OK' OR t.kvittering_status = 'IKKE_MOTTATT'""",
                mapOf("trekkIdSke" to trekkIdSke),
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

    fun updatePeriodeStatus(periode: PeriodeTilOS, status: PeriodeStatus, session: kotliquery.TransactionalSession) {
    }

    fun insertTrekkForOS(nyPeriode: PeriodeTilOS, session: Session) {}

    fun getTransaksjonerTilOsSomIkkeErSendt(session: Session) {
        session.list(
            queryOf(
                """
                select * from transaksjon_os where transaksjon_status is null or transaksjon_status = 'IKKE_SENDT'
                """.trimIndent(),
            ),
        ) { row ->
            val transaksjonId = row.string("transaksjon_id")
            val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

            TransaksjonOS(row, perioderTilOS)
        }
    }

    private fun getPerioderForTransaksjon(transaksjonId: String, session: Session): MutableList<PeriodeTilOS> {
        val perioderTilOS = mutableListOf<PeriodeTilOS>()
        session.list(
            queryOf(
                """
                select * from periode_til_os where transaksjons_os_id = :transaksjonId
                """.trimIndent(),
                mapOf("transaksjonId" to transaksjonId),
            ),
        ) { periodeRow ->
            perioderTilOS.add(PeriodeTilOS(periodeRow))
        }
        return perioderTilOS
    }
}