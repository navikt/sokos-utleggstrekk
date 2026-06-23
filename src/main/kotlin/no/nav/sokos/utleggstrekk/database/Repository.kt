package no.nav.sokos.utleggstrekk.database

import java.time.LocalDateTime
import javax.sql.DataSource

import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
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
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus

private val logger = KotlinLogging.logger { }

const val ANTALL_MND_AVSLUTTEDE_TREKK_TAS_VARE_PAA = 6L

class Repository(private val dataSource: DataSource) {
    fun deleteOldData() {
        val sixMonthsAgo = LocalDateTime.now().minusMonths(ANTALL_MND_AVSLUTTEDE_TREKK_TAS_VARE_PAA)
        dataSource.withTransaction { session ->
            val expiredFraskatt =
                """
                WITH expired_fraskatt AS (
                    SELECT DISTINCT trekkid 
                        FROM fraskatt 
                        WHERE tidspunkt_opprettet<:threshold 
                            AND trekkstatus=:trekkstatus
                )
                """.trimIndent()
            val parameters = mapOf("threshold" to sixMonthsAgo, "trekkstatus" to Trekkstatus.AVSLUTTET.name)
            val transaksjonOsDeleted =
                session.update(
                    queryOf(
                        // language=SQL
                        """
                        $expiredFraskatt
                        DELETE FROM transaksjon_os t USING expired_fraskatt ef WHERE t.trekk_id_ske = ef.trekkid
                        """.trimIndent(),
                        parameters,
                    ),
                )
            val fraskattDeleted =
                session.update(
                    queryOf(
                        // language=SQL
                        """
                        $expiredFraskatt
                        DELETE FROM fraskatt f USING expired_fraskatt ef WHERE f.trekkid = ef.trekkid
                        """.trimIndent(),
                        parameters,
                    ),
                )

            if (fraskattDeleted != 0 || transaksjonOsDeleted != 0) {
                logger.info("Slettet $fraskattDeleted trekkversjoner fra Skatt og $transaksjonOsDeleted fra transaksjon_os")
            }
        }
    }

    fun insertTrekkFraSkatt(trekkpaalegg: Trekkpaalegg, status: SkattTrekkStatus = SkattTrekkStatus.MOTTATT): Long? =
        dataSource.withTransaction { session ->
            val fraSkattId =
                session.updateAndReturnGeneratedKey(
                    queryOf(
                        // language=SQL
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
                        ) VALUES(:trekkid, :sekvensnummer, :trekkversjon, :opprettet, :saksnummer, :trekkpliktig, :skyldner, :trekkstatus)
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
                        // language=SQL
                        """
                        INSERT INTO periode(
                            fraskatt_id,
                            trekk_id_ske,
                            dato_start, 
                            dato_slutt,
                            trekkbelop,
                            trekkprosent
                        ) VALUES(:fraskattID, :trekkIDSke, :startdato, :sluttDato, :trekkBelop, :trekkProsent)     
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
                    // language=SQL
                    """
                    INSERT INTO betalingsinformasjonfraskatt(
                        fraskatt_id,
                        betalingsmottaker,
                        kidnummer,
                        kontonummer
                    ) VALUES(:fraskattID, :betalingsmottaker, :kidnummer, :kontonummer)   
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
                    // language=SQL
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
            return@withTransaction fraSkattId
        }

    fun insertFeilmeldingFraOS(kvittering: KvitteringFraOppdrag) {
        dataSource.withTransaction { session ->
            session.update(
                queryOf(
                    // language=SQL
                    """
                    INSERT INTO feilmelding (
                        kreditor_trekk_id,
                        transaksjons_id,
                        trekkalternativ,
                        feilkode,
                        beskrivelse
                    ) VALUES (
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
    }

    fun insertTransaksjonTilOs(dto: OSDto, session: TransactionalSession) {
        val id =
            session.updateAndReturnGeneratedKey(
                queryOf(
                    // language=SQL
                    """
                    INSERT INTO transaksjon_os (
                         transaksjons_id, 
                         transaksjon_status, 
                         trekk_id_ske, 
                         trekkversjon, 
                         kvittering_status, 
                         aksjonskode, 
                         kreditor_id_tss, 
                         kreditor_trekk_id, 
                         kreditorsref, 
                         debitor_id, 
                         trekk_alternativ, 
                         trekk_type, 
                         kid,
                         kilde,
                         dokument_json, 
                         prioritet_fom_dato,
                         gyldig_tom_dato
                    ) VALUES(
                        :transaksjonsId,
                        :transaksjonStatus,
                        :trekkIdSke,
                        :trekkversjon,
                        :kvitteringStatus,
                        :aksjonskode,
                        :kreditorIdTss,
                        :kreditorTrekkId,
                        :kreditorsref,
                        :debitorId,
                        :trekkalternativ,
                        :trekkType,
                        :kid,
                        :kilde,
                        :dokumentJson,
                        :prioritetFomDato,
                        :gyldigTomDato
                    )
                    """.trimIndent(),
                    mapOf(
                        "transaksjonsId" to dto.transaksjonID,
                        "transaksjonStatus" to TransaksjonsStatus.IKKE_SENDT.name,
                        "trekkIdSke" to dto.trekkIDSke,
                        "trekkversjon" to dto.trekkversjon,
                        "kvitteringStatus" to KvitteringStatus.IKKE_MOTTATT.name,
                        "aksjonskode" to dto.innrapporteringTrekk.aksjonskode.name,
                        "kreditorIdTss" to dto.innrapporteringTrekk.kreditorIdTss,
                        "kreditorTrekkId" to dto.innrapporteringTrekk.kreditorTrekkId,
                        "kreditorsref" to dto.innrapporteringTrekk.kreditorsRef,
                        "debitorId" to dto.innrapporteringTrekk.debitorId,
                        "trekkalternativ" to dto.innrapporteringTrekk.kodeTrekkAlternativ.name,
                        "trekkType" to dto.innrapporteringTrekk.kodeTrekktype,
                        "kid" to dto.innrapporteringTrekk.kid,
                        "kilde" to dto.innrapporteringTrekk.kilde,
                        "dokumentJson" to dto.documentJson,
                        "prioritetFomDato" to dto.innrapporteringTrekk.prioritetFomDato,
                        "gyldigTomDato" to dto.innrapporteringTrekk.gyldigTomDato,
                    ),
                ),
            )
        dto.innrapporteringTrekk.perioder?.periode?.forEach { periode ->
            session.update(
                queryOf(
                    // language=SQL
                    """ 
                    INSERT INTO periode_til_os (
                        transaksjon_os_id, sats, periode_fom_dato, periode_tom_dato
                    )
                    VALUES(
                        :transaksjonOSForeignKey,
                        :sats,
                        :periodeFom,
                        :periodeTom
                    )
                    """.trimIndent(),
                    mapOf(
                        "transaksjonOSForeignKey" to id,
                        "sats" to periode.sats,
                        "periodeFom" to periode.periodeFomDato,
                        "periodeTom" to periode.periodeTomDato,
                    ),
                ),
            )
        }
    }

    fun updateTransaksjonSendt(transaksjonId: String) {
        dataSource.withTransaction { session ->
            session.update(
                queryOf(
                    // language=SQL
                    """
                    UPDATE  transaksjon_os  
                    SET 
                        transaksjon_status=:transaksjonStatus,
                        tidspunkt_siste_status=NOW(),
                        tidspunkt_sendt=NOW()
                    WHERE transaksjons_id=:transaksjonsId
                    """.trimIndent(),
                    mapOf(
                        "transaksjonStatus" to TransaksjonsStatus.SENDT.name,
                        "transaksjonsId" to transaksjonId,
                    ),
                ),
            )
        }
    }

    fun updateTransaksjonValideringsfeil(transaksjonId: String) {
        dataSource.withTransaction { session ->
            session.update(
                queryOf(
                    // language=SQL
                    """
                    UPDATE  transaksjon_os  
                    SET 
                        transaksjon_status=:transaksjonStatus,
                        tidspunkt_siste_status=NOW()
                    WHERE transaksjons_id=:transaksjonsId
                    """.trimIndent(),
                    mapOf(
                        "transaksjonStatus" to TransaksjonsStatus.VALIDERINGSFEIL.name,
                        "transaksjonsId" to transaksjonId,
                    ),
                ),
            )
        }
    }

    fun updateReceiptStatusOfTransaksjon(transaksjonId: String, kvitteringStatus: KvitteringStatus, navTrekkId: String) {
        dataSource.withTransaction { session ->
            session.update(
                queryOf(
                    // language=SQL
                    """
                    UPDATE  transaksjon_os  
                    SET 
                        kvittering_status=:kvitteringStatus,
                        nav_trekk_id=:navTrekkId
                    WHERE 
                        transaksjons_id=:transaksjonsId
                    """.trimIndent(),
                    mapOf(
                        "kvitteringStatus" to kvitteringStatus.name,
                        "navTrekkId" to navTrekkId.ifEmpty { INGEN_TREKK_ID_I_KVITTERING },
                        "transaksjonsId" to transaksjonId,
                    ),
                ),
            )
        }
    }

    fun getTransaksjonerTilOsForTrekkID(trekkIdSke: String): List<TransaksjonOS> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    // language=SQL
                    """
                    SELECT * FROM transaksjon_os WHERE 
                        trekk_id_ske=:trekkIdSke
                    """.trimIndent(),
                    mapOf("trekkIdSke" to trekkIdSke),
                ),
            ) { row ->
                val transaksjonId = row.long("id")
                val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)
                TransaksjonOS(row, perioderTilOS)
            }
        }

    fun getSkattTrekkStatus(fraSkattId: Long, session: TransactionalSession): SkattTrekkStatus =
        session.single(
            queryOf(
                // language=SQL
                "SELECT status FROM fraskatt_status WHERE fraskatt_id = :fraSkattId",
                mapOf("fraSkattId" to fraSkattId),
            ),
        ) { row ->
            SkattTrekkStatus.valueOf(row.string("status"))
        } ?: throw IllegalArgumentException("Ingen status funnet for fraskatt_id $fraSkattId")

    fun updateTrekkFraSkattStatus(fraSkattId: Long, status: SkattTrekkStatus) = dataSource.withTransaction { session -> updateTrekkFraSkattStatus(fraSkattId, status, session) }

    fun updateTrekkFraSkattStatus(fraSkattId: Long, status: SkattTrekkStatus, session: TransactionalSession) {
        session.update(
            queryOf(
                // language=SQL
                "UPDATE fraskatt_status SET status = :status, tidspunkt_satt = NOW() WHERE fraskatt_id = :fraSkattId",
                mapOf("fraSkattId" to fraSkattId, "status" to status.name),
            ),
        )
    }

    /** Henter en spesifik versjon av et trekk gitt fraskatt_id */
    fun getTrekkFraSkatt(id: Long, session: TransactionalSession): TrekkFraSkatt =
        session.single(
            queryOf(
                // language=SQL
                """SELECT * FROM fraskatt WHERE id=:id""".trimIndent(),
                mapOf("id" to id),
            ),
        ) { row -> TrekkFraSkatt(row) } ?: throw IllegalArgumentException("Trekk med id $id finnes ikke")

    fun getPerioderForTrekkVersjon(fraSkattId: Long): List<PeriodeFraSkatt> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    // language=SQL
                    """
                    SELECT * FROM periode p WHERE fraskatt_id=:fraSkattId ORDER BY p.dato_start ASC
                    """.trimIndent(),
                    mapOf(
                        "fraSkattId" to fraSkattId,
                    ),
                ),
            ) { row -> PeriodeFraSkatt(row) }
        }

    fun getPerioderForTrekk(trekkFraSkatt: TrekkFraSkatt): List<PeriodeFraSkatt> = getPerioderForTrekkVersjon(trekkFraSkatt.id)

    fun getPerioderTilOs(trekkIdSke: String, alternativ: TrekkAlternativ): List<PeriodeTilOS> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    // language=SQL
                    """
                        SELECT * 
                            FROM periode_til_os p 
                            JOIN  transaksjon_os  t ON p.transaksjon_os_id = t.id 
                            WHERE trekk_id_ske=:trekkIdSke 
                                AND t.trekk_alternativ=:trekkAlternativ 
                                AND t.transaksjon_status=:transaksjonStatus
                                AND t.kvittering_status IN (:IKKE_MOTTATT, :OK)
                            ORDER BY p.id ASC 
                """,
                    mapOf(
                        "trekkIdSke" to trekkIdSke,
                        "trekkAlternativ" to alternativ.name,
                        "transaksjonStatus" to TransaksjonsStatus.SENDT.name,
                        "OK" to KvitteringStatus.OK.name,
                        "IKKE_MOTTATT" to KvitteringStatus.IKKE_MOTTATT.name,
                    ),
                ),
            ) { row -> PeriodeTilOS(row) }
        }

    fun getBetalingsinformasjonForTrekk(id: Long): BetalingsinformasjonFraSkatt? =
        using(sessionOf(dataSource)) { session ->
            session.single(
                queryOf(
                    // language=SQL
                    """SELECT * FROM betalingsinformasjonfraskatt WHERE fraskatt_id=:id""".trimIndent(),
                    mapOf("id" to id),
                ),
            ) { row -> BetalingsinformasjonFraSkatt(row) }
        }

    fun getLastSekvensnummer(): Int =
        using(sessionOf(dataSource)) { session ->
            session.single(
                queryOf(
                    // language=SQL
                    """SELECT sekvensnummer FROM fraskatt ORDER BY sekvensnummer DESC LIMIT 1""",
                ),
            ) { row -> row.intOrNull(1) } ?: 0
        }

    fun getTransaksjonerTilOsSomIkkeErSendt(): List<TransaksjonOS> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    // language=SQL
                    """
                    SELECT * FROM  transaksjon_os WHERE transaksjon_status IS null OR transaksjon_status = :IKKE_SENDT
                        ORDER BY id ASC
                    """.trimIndent(),
                    mapOf("IKKE_SENDT" to TransaksjonsStatus.IKKE_SENDT.name),
                ),
            ) { row ->
                val transaksjonId = row.long("id")
                val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

                TransaksjonOS(row, perioderTilOS)
            }
        }

    fun getTransakjonerTilOsSomManglerKvittering(): List<TransaksjonOS> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    // language=SQL
                    """
                    SELECT * FROM transaksjon_os
                    WHERE transaksjon_status = :SENDT
                    AND kvittering_status = :IKKE_MOTTATT
                    """.trimIndent(),
                    mapOf(
                        "SENDT" to TransaksjonsStatus.SENDT.name,
                        "IKKE_MOTTATT" to KvitteringStatus.IKKE_MOTTATT.name,
                    ),
                ),
            ) { row ->
                val transaksjonId = row.long("id")
                val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

                TransaksjonOS(row, perioderTilOS)
            }
        }

    fun getPerioderForTransaksjon(transaksjonOSId: Long, session: Session): List<PeriodeTilOS> =
        session.list(
            queryOf(
                // language=SQL
                """
                SELECT * FROM periode_til_os WHERE transaksjon_os_id = :transaksjonOSId
                """.trimIndent(),
                mapOf("transaksjonOSId" to transaksjonOSId),
            ),
        ) { periodeRow -> PeriodeTilOS(periodeRow) }

    fun getOsAlternativForTrekk(trekk: TrekkFraSkatt, session: TransactionalSession): Set<TrekkAlternativ> =
        session
            .list(
                queryOf(
                    // language=SQL
                    """
                    SELECT DISTINCT trekk_alternativ from  transaksjon_os  WHERE trekk_id_ske=:trekkIdSke 
                    AND
                    kvittering_status NOT IN (:FEIL, :UKJENT)
                    AND
                    transaksjon_status = :SENDT
                    """.trimIndent(),
                    mapOf(
                        "trekkIdSke" to trekk.trekkid,
                        "FEIL" to KvitteringStatus.FEIL.name,
                        "UKJENT" to KvitteringStatus.UKJENT.name,
                        "SENDT" to TransaksjonsStatus.SENDT.name,
                    ),
                ),
            ) { row ->
                TrekkAlternativ.valueOf(row.string("trekk_alternativ").uppercase())
            }.toSet()

    fun <A> withTransaction(operation: (TransactionalSession) -> A): A = dataSource.withTransaction(operation)

    fun countUtleggstrekk(): Map<Trekkstatus, Long> =
        using(sessionOf(dataSource)) { session ->
            session
                .list(
                    queryOf(
                        // language=SQL
                        """
                        SELECT t.trekkstatus, COUNT(*) AS count
                        FROM (
                            SELECT DISTINCT ON (f.trekkid) f.trekkid, f.trekkstatus
                            FROM fraskatt f
                            WHERE NOT EXISTS (
                                SELECT 1
                                FROM transaksjon_os tx
                                WHERE tx.trekk_id_ske = f.trekkid
                                AND tx.kvittering_status = :FEIL
                             )
                             ORDER BY f.trekkid, f.trekkversjon DESC
                         ) t
                        GROUP BY t.trekkstatus;
                        """.trimIndent(),
                        mapOf("FEIL" to KvitteringStatus.FEIL.name),
                    ),
                ) { row -> Trekkstatus.valueOf(row.string("trekkstatus")) to row.long("count") }
                .toMap()
        }

    fun countKvitterteTrekkTilOS(): Map<TrekkAlternativ, Long> =
        using(sessionOf(dataSource)) { session ->
            session
                .list(
                    queryOf(
                        // language=SQL
                        """
                        SELECT trekk_alternativ, COUNT(*) AS count
                            FROM ( 
                                SELECT DISTINCT ON (t.trekk_id_ske) t.trekk_id_ske, t.trekk_alternativ FROM transaksjon_os t                                                                                                                                             
                                    WHERE t.kvittering_status = :kvitteringStatus AND NOT EXISTS (                                                                                                                                                      
                                        SELECT 1 FROM transaksjon_os t2 
                                            WHERE t2.trekk_id_ske = t.trekk_id_ske AND t2.gyldig_tom_dato IS NOT NULL
                                    ) ORDER BY t.trekk_id_ske, t.trekkversjon DESC
                            ) t GROUP BY trekk_alternativ
                        """.trimIndent(),
                        mapOf("kvitteringStatus" to KvitteringStatus.OK.name),
                    ),
                ) { row -> TrekkAlternativ.valueOf(row.string("trekk_alternativ")) to row.long("count") }
                .toMap()
        }

    fun getNyesteTrekkVersjon(trekkId: String, session: TransactionalSession): TrekkFraSkatt =
        session.single(
            queryOf(
                // language=SQL
                """
                    |SELECT * FROM fraskatt
                    |WHERE trekkid=:trekkid ORDER BY trekkversjon DESC LIMIT 1
                """.trimMargin(),
                mapOf("trekkid" to trekkId),
            ),
        ) { row ->
            TrekkFraSkatt(row)
        } ?: throw IllegalStateException("Fant ikke trekk fra skatt for $trekkId")

    fun getTrekkIdTilTrekkSomSkalBehandles(): List<Long> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    // language=SQL
                    """
                    SELECT f.id FROM fraskatt f
                        LEFT JOIN  fraskatt_status t ON t.fraskatt_id = f.id
                        WHERE t.status IS NULL 
                            OR t.status IN (:MOTTATT, :REPETERES)
                        ORDER BY f.sekvensnummer ASC
                    """.trimIndent(),
                    mapOf(
                        "MOTTATT" to SkattTrekkStatus.MOTTATT.name,
                        "REPETERES" to SkattTrekkStatus.REPETERES.name,
                    ),
                ),
            ) { row -> row.long(1) }
        }
}

fun <A> DataSource.withTransaction(operation: (TransactionalSession) -> A): A =
    using(sessionOf(this, returnGeneratedKey = true)) { session ->
        session.transaction { tx ->
            operation(tx)
        }
    }
