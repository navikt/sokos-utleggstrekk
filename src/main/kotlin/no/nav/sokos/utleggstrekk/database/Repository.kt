package no.nav.sokos.utleggstrekk.database

import java.time.LocalDateTime

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging

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
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus

private val logger = KotlinLogging.logger { }

// TODO: Ikke bruk "withTransaction" med "select". Vent til vi har kotliquery
// TODO: Flytte funksjoner som brukes bare i test til test filer
class Repository(private val dataSource: HikariDataSource) {
    fun deleteOldData() {
        val sixMonthsAgo = LocalDateTime.now().minusMonths(6)

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

    fun doesTrekkExist(trekkId: String, trekkversjon: Int): Boolean =
        dataSource.withTransaction { session ->
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
        }

    fun insertTrekkFraSkatt(trekkpaalegg: Trekkpaalegg, status: SkattTrekkStatus = SkattTrekkStatus.MOTTATT): Long? =
        dataSource.withTransaction { session ->
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

    // TODO: Ta den bort og pass på at det er ingen skrivefeil
    object TransaksjonOsTable {
        const val TABLE_NAME = "transaksjon_os"
        const val ID_COLUMN = "id"
        const val NAV_TREKK_ID_PARAM = "navTrekkId"
        const val TRANSAKSJONS_ID_PARAM = "transaksjonsId"
        const val TRANSAKSJON_STATUS_PARAM = "transaksjonStatus"
        const val TREKK_ID_SKE_PARAM = "trekkIdSke"
        const val TREKKVERSJON_PARAM = "trekkversjon"
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
        const val DOKUMENT_JSON_PARAM = "dokumentJson"
        const val PRIORITET_FOM_DATO_PARAM = "prioritetFomDato"
        const val GYLDIG_TOM_DATO_PARAM = "gyldigTomDato"
        const val NAV_TREKK_ID_COLUMN = "nav_trekk_id"
        const val TRANSAKSJONS_ID_COLUMN = "transaksjons_id"
        const val TRANSAKSJON_STATUS_COLUMN = "transaksjon_status"
        const val TREKK_ID_SKE_COLUMN = "trekk_id_ske"
        const val TREKKVERSJON_COLUMN = "trekkversjon"
        const val KVITTERING_STATUS_COLUMN = "kvittering_status"
        const val TIDSPUNKT_SENDT_COLUMN = "tidspunkt_sendt"
        const val TIDSPUNKT_SISTE_STATUS_COLUMN = "tidspunkt_siste_status"
        const val AKSJONSKODE_COLUMN = "aksjonskode"
        const val KREDITOR_ID_TSS_COLUMN = "kreditor_id_tss"
        const val KREDITOR_TREKK_ID_COLUMN = "kreditor_trekk_id"
        const val KREDITORSREF_COLUMN = "kreditorsref"
        const val DEBITOR_ID_COLUMN = "debitor_id"
        const val TREKK_ALTERNATIV_COLUMN = "trekk_alternativ"
        const val TREKK_TYPE_COLUMN = "trekk_type"
        const val KID_COLUMN = "kid"
        const val KILDE_COLUMN = "kilde"
        const val SALDO_COLUMN = "saldo"
        const val PRIORITET_FOM_DATO_COLUMN = "prioritet_fom_dato"
        const val GYLDIG_TOM_DATO_COLUMN = "gyldig_tom_dato"
        const val DOKUMENT_JSON_COLUMN = "dokument_json"
    }

    fun insertTransaksjonTilOs(dto: OSDto) = dataSource.withTransaction { session -> insertTransaksjonTilOs(dto, session) }

    fun insertTransaksjonTilOs(dto: OSDto, session: TransactionalSession) {
        val id =
            session.updateAndReturnGeneratedKey(
                queryOf(
                    """
                      INSERT INTO 
                    ${TransaksjonOsTable.TABLE_NAME} (
                          ${TransaksjonOsTable.TRANSAKSJONS_ID_COLUMN}, 
                           ${TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN}, 
                           ${TransaksjonOsTable.TREKK_ID_SKE_COLUMN}, 
                           ${TransaksjonOsTable.TREKKVERSJON_COLUMN}, 
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
                           ${TransaksjonOsTable.DOKUMENT_JSON_COLUMN}, 
                           ${TransaksjonOsTable.PRIORITET_FOM_DATO_COLUMN},
                           ${TransaksjonOsTable.GYLDIG_TOM_DATO_COLUMN}
                      ) VALUES(
                          :${TransaksjonOsTable.TRANSAKSJONS_ID_PARAM},
                          :${TransaksjonOsTable.TRANSAKSJON_STATUS_PARAM},
                          :${TransaksjonOsTable.TREKK_ID_SKE_PARAM},
                          :${TransaksjonOsTable.TREKKVERSJON_PARAM},
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
                          :${TransaksjonOsTable.DOKUMENT_JSON_PARAM},
                          :${TransaksjonOsTable.PRIORITET_FOM_DATO_PARAM},
                          :${TransaksjonOsTable.GYLDIG_TOM_DATO_PARAM}
                      )
                    """.trimIndent(),
                    mapOf(
                        TransaksjonOsTable.TRANSAKSJONS_ID_PARAM to dto.transaksjonID,
                        TransaksjonOsTable.TRANSAKSJON_STATUS_PARAM to TransaksjonsStatus.IKKE_SENDT.name,
                        TransaksjonOsTable.TREKK_ID_SKE_PARAM to dto.trekkIDSke,
                        TransaksjonOsTable.TREKKVERSJON_PARAM to dto.trekkversjon,
                        TransaksjonOsTable.KVITTERING_STATUS_PARAM to KvitteringStatus.IKKE_MOTTATT.name,
                        TransaksjonOsTable.AKSJONSKODE_PARAM to dto.innrapporteringTrekk.aksjonskode.name,
                        TransaksjonOsTable.KREDITOR_ID_TSS_PARAM to dto.innrapporteringTrekk.kreditorIdTss,
                        TransaksjonOsTable.KREDITOR_TREKK_ID_PARAM to dto.innrapporteringTrekk.kreditorTrekkId,
                        TransaksjonOsTable.KREDITORSREF_PARAM to dto.innrapporteringTrekk.kreditorsRef,
                        TransaksjonOsTable.DEBITOR_ID_PARAM to dto.innrapporteringTrekk.debitorId,
                        TransaksjonOsTable.TREKKALTERNATIV_PARAM to dto.innrapporteringTrekk.kodeTrekkAlternativ.name,
                        TransaksjonOsTable.TREKK_TYPE_PARAM to dto.innrapporteringTrekk.kodeTrekktype,
                        TransaksjonOsTable.KID_PARAM to dto.innrapporteringTrekk.kid,
                        TransaksjonOsTable.KILDE_PARAM to dto.innrapporteringTrekk.kilde,
                        TransaksjonOsTable.DOKUMENT_JSON_PARAM to dto.documentJson,
                        TransaksjonOsTable.PRIORITET_FOM_DATO_PARAM to dto.innrapporteringTrekk.prioritetFomDato,
                        TransaksjonOsTable.GYLDIG_TOM_DATO_PARAM to dto.innrapporteringTrekk.gyldigTomDato,
                    ),
                ),
            )
        dto.innrapporteringTrekk.perioder?.periode?.forEach { periode ->
            session.update(
                queryOf(
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
                    """
                    UPDATE  ${TransaksjonOsTable.TABLE_NAME}  
                    SET 
                    ${TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN}=:${TransaksjonOsTable.TRANSAKSJON_STATUS_PARAM},
                    ${TransaksjonOsTable.TIDSPUNKT_SISTE_STATUS_COLUMN}=NOW(),
                    ${TransaksjonOsTable.TIDSPUNKT_SENDT_COLUMN}=NOW()
                    WHERE ${TransaksjonOsTable.TRANSAKSJONS_ID_COLUMN}=:${TransaksjonOsTable.TRANSAKSJONS_ID_PARAM}
                    """.trimIndent(),
                    mapOf(
                        TransaksjonOsTable.TRANSAKSJON_STATUS_PARAM to TransaksjonsStatus.SENDT.name,
                        TransaksjonOsTable.TRANSAKSJONS_ID_PARAM to transaksjonId,
                    ),
                ),
            )
        }
    }

    fun updateReceiptStatusOfTransaksjon(transaksjonId: String, kvitteringStatus: KvitteringStatus, navTrekkId: String) {
        dataSource.withTransaction { session ->
            session.update(
                queryOf(
                    """
                    UPDATE  ${TransaksjonOsTable.TABLE_NAME}  
                    SET 
                    ${TransaksjonOsTable.KVITTERING_STATUS_COLUMN}=:${TransaksjonOsTable.KVITTERING_STATUS_PARAM},
                      ${TransaksjonOsTable.NAV_TREKK_ID_COLUMN}=:${TransaksjonOsTable.NAV_TREKK_ID_PARAM}
                    WHERE 
                      ${TransaksjonOsTable.TRANSAKSJONS_ID_COLUMN}=:${TransaksjonOsTable.TRANSAKSJONS_ID_PARAM}
                    """.trimIndent(),
                    mapOf(
                        TransaksjonOsTable.KVITTERING_STATUS_PARAM to kvitteringStatus.name,
                        TransaksjonOsTable.NAV_TREKK_ID_PARAM to navTrekkId.ifEmpty { INGEN_TREKK_ID_I_KVITTERING },
                        TransaksjonOsTable.TRANSAKSJONS_ID_PARAM to transaksjonId,
                    ),
                ),
            )
        }
    }

    fun getTransaksjonTilOs(transaksjonsId: String): TransaksjonOS? =
        dataSource.withTransaction { session ->
            session.single(
                queryOf(
                    """
                    SELECT * FROM  ${TransaksjonOsTable.TABLE_NAME}  WHERE 
                    ${TransaksjonOsTable.TRANSAKSJONS_ID_COLUMN}=:${TransaksjonOsTable.TRANSAKSJONS_ID_PARAM}
                    """.trimIndent(),
                    mapOf(TransaksjonOsTable.TRANSAKSJONS_ID_PARAM to transaksjonsId),
                ),
            ) { row ->
                val transaksjonId = row.long(TransaksjonOsTable.ID_COLUMN)
                val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

                TransaksjonOS(row, perioderTilOS)
            }
        }

    fun getTransaksjonerTilOsForTrekkID(trekkIdSke: String): List<TransaksjonOS> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """
                    SELECT * FROM ${TransaksjonOsTable.TABLE_NAME} WHERE 
                    ${TransaksjonOsTable.TREKK_ID_SKE_COLUMN}=:${TransaksjonOsTable.TREKK_ID_SKE_PARAM}
                    """.trimIndent(),
                    mapOf(TransaksjonOsTable.TREKK_ID_SKE_PARAM to trekkIdSke),
                ),
            ) { row ->
                val transaksjonId = row.long("id")
                val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

                TransaksjonOS(row, perioderTilOS)
            }
        }

    fun getAllTransaksjonerTilOs(): List<TransaksjonOS> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """
                    SELECT * FROM  ${TransaksjonOsTable.TABLE_NAME} 
                    """.trimIndent(),
                ),
            ) { row ->
                val transaksjonId = row.long("id")
                val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

                TransaksjonOS(row, perioderTilOS)
            }
        }

    fun getTrekkFraSkattMedStatus(status: SkattTrekkStatus): List<TrekkFraSkatt> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    "SELECT * FROM fraskatt f JOIN fraskatt_status s ON f.id = s.fraskatt_id WHERE s.status=:status ORDER BY f.sekvensnummer ASC",
                    mapOf("status" to status.name),
                ),
            ) { row ->
                TrekkFraSkatt(row)
            }
        }

    fun getSkattTrekkStatus(fraSkattId: Long, session: TransactionalSession): SkattTrekkStatus =
        session.single(
            queryOf(
                "SELECT status FROM fraskatt_status WHERE fraskatt_id = :fraSkattId",
                mapOf("fraSkattId" to fraSkattId),
            ),
        ) { row ->
            SkattTrekkStatus.valueOf(row.string("status"))
        } ?: throw IllegalArgumentException("Ingen status funnet for fraskatt_id $fraSkattId")

    fun getTrekkFraSkattStatus(id: Long): SkattTrekkStatus? =
        dataSource.withTransaction { session ->
            session.single(
                queryOf(
                    "SELECT status FROM fraskatt f JOIN fraskatt_status s ON f.id = s.fraskatt_id WHERE f.id=:id",
                    mapOf("id" to id),
                ),
            ) { row ->
                SkattTrekkStatus.valueOf(row.string("status"))
            }
        }

    fun updateTrekkFraSkattStatus(fraSkattId: Long, status: SkattTrekkStatus) = dataSource.withTransaction { session -> updateTrekkFraSkattStatus(fraSkattId, status, session) }

    fun updateTrekkFraSkattStatus(fraSkattId: Long, status: SkattTrekkStatus, session: TransactionalSession) {
        session.update(
            queryOf(
                "UPDATE fraskatt_status SET status = :status, tidspunkt_satt = NOW() WHERE fraskatt_id = :fraSkattId",
                mapOf("fraSkattId" to fraSkattId, "status" to status.name),
            ),
        )
    }

    fun getFeilmeldingerFraOS(transaksjonsId: String): Feilmelding? =
        dataSource.withTransaction { session ->
            session.single(
                queryOf(
                    "SELECT * FROM feilmelding WHERE transaksjons_id=:transaksjonsId",
                    mapOf("transaksjonsId" to transaksjonsId),
                ),
            ) { row -> Feilmelding(row) }
        }

    fun getAllTrekkFraSkatt(): List<TrekkFraSkatt> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """SELECT * FROM fraskatt""".trimIndent(),
                ),
            ) { row -> TrekkFraSkatt(row) }
        }

    fun getTrekkFraSkatt(id: Long): TrekkFraSkatt? =
        dataSource.withTransaction { session ->
            getTrekkFraSkatt(id, session)
        }

    /** Henter en spesifik versjon av et trekk gitt fraskatt_id */
    fun getTrekkFraSkatt(id: Long, session: TransactionalSession): TrekkFraSkatt =
        session.single(
            queryOf(
                """SELECT * FROM fraskatt WHERE id=:id""".trimIndent(),
                mapOf("id" to id),
            ),
        ) { row -> TrekkFraSkatt(row) } ?: throw IllegalArgumentException("Trekk med id $id finnes ikke")

    fun getTrekkFraSkatt(trekkid: String, versjon: Int): TrekkFraSkatt? =
        dataSource.withTransaction { session ->
            session.single(
                queryOf(
                    """SELECT * FROM fraskatt WHERE trekkid=:trekkid AND trekkversjon=:versjon""".trimIndent(),
                    mapOf("trekkid" to trekkid, "versjon" to versjon),
                ),
            ) { row -> TrekkFraSkatt(row) }
        }

    fun getPerioderForTrekkVersjon(fraSkattId: Long): List<PeriodeFraSkatt> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
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

    fun getTrekkAlternativOS(trekkIdSke: String): List<TrekkAlternativ> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """
                        SELECT DISTINCT ${TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN}
                        FROM  ${TransaksjonOsTable.TABLE_NAME}  
                        WHERE ${TransaksjonOsTable.TREKK_ID_SKE_COLUMN}=:${TransaksjonOsTable.TREKK_ID_SKE_PARAM}
                        AND (${TransaksjonOsTable.KVITTERING_STATUS_COLUMN} = '${KvitteringStatus.OK.name}' 
                        OR ${TransaksjonOsTable.KVITTERING_STATUS_COLUMN}  = '${KvitteringStatus.IKKE_MOTTATT.name}')
                    """.trimMargin(),
                    mapOf(TransaksjonOsTable.TREKK_ID_SKE_PARAM to trekkIdSke),
                ),
            ) { row -> TrekkAlternativ.valueOf(row.string(TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN).uppercase()) }
        }

    fun getPerioderTilOs(trekkIdSke: String, alternativ: TrekkAlternativ): List<PeriodeTilOS> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """
                        SELECT * 
                        FROM periode_til_os p 
                        JOIN  ${TransaksjonOsTable.TABLE_NAME}  t ON p.transaksjon_os_id = t.id 
                        WHERE trekk_id_ske=:trekkIdSke 
                        AND t.trekk_alternativ=:trekkAlternativ 
                        AND t.kvittering_status IN ('${KvitteringStatus.IKKE_MOTTATT.name}', '${KvitteringStatus.OK.name}')
                        ORDER BY p.id ASC 
                """,
                    mapOf(
                        "trekkIdSke" to trekkIdSke,
                        "trekkAlternativ" to alternativ.name,
                    ),
                ),
            ) { row -> PeriodeTilOS(row) }
        }

    fun getBetalingsinformasjonForTrekk(id: Long): BetalingsinformasjonFraSkatt? =
        dataSource.withTransaction { session ->
            session.single(
                queryOf(
                    """SELECT * FROM betalingsinformasjonfraskatt WHERE fraskatt_id=:id""".trimIndent(),
                    mapOf("id" to id),
                ),
            ) { row -> BetalingsinformasjonFraSkatt(row) }
        }

    fun getLastSekvensnummer(): Int =
        dataSource.withTransaction { session ->
            session.single(
                queryOf("""SELECT sekvensnummer FROM fraskatt ORDER BY sekvensnummer DESC LIMIT 1"""),
            ) { row -> row.intOrNull(1) } ?: 0
        }

    fun getTransaksjonerTilOsSomIkkeErSendt(): List<TransaksjonOS> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """
                    SELECT * FROM  ${TransaksjonOsTable.TABLE_NAME} WHERE ${TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN} IS null OR ${TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN}  = '${TransaksjonsStatus.IKKE_SENDT.name}'
                        ORDER BY id ASC
                    """.trimIndent(),
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
                    """
                    SELECT * FROM ${TransaksjonOsTable.TABLE_NAME}
                    WHERE ${TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN} = '${TransaksjonsStatus.SENDT.name}'
                    AND ${TransaksjonOsTable.KVITTERING_STATUS_COLUMN} = '${KvitteringStatus.IKKE_MOTTATT.name}'
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
                SELECT * FROM periode_til_os WHERE transaksjon_os_id = :transaksjonOSId
                """.trimIndent(),
                mapOf("transaksjonOSId" to transaksjonOSId),
            ),
        ) { periodeRow ->
            perioderTilOS.add(PeriodeTilOS(periodeRow))
        }
        return perioderTilOS.toList()
    }

    fun getOsAlternativForTrekk(trekk: TrekkFraSkatt, session: TransactionalSession): Set<TrekkAlternativ> =
        session
            .list(
                queryOf(
                    """
                    SELECT DISTINCT ${TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN} from  ${TransaksjonOsTable.TABLE_NAME}  WHERE ${TransaksjonOsTable.TREKK_ID_SKE_COLUMN}=:${TransaksjonOsTable.TREKK_ID_SKE_PARAM} 
                    AND
                    ${TransaksjonOsTable.KVITTERING_STATUS_COLUMN} NOT IN ('${KvitteringStatus.FEIL.name}', '${KvitteringStatus.UKJENT.name}')
                    """.trimIndent(),
                    mapOf(TransaksjonOsTable.TREKK_ID_SKE_PARAM to trekk.trekkid),
                ),
            ) { row ->
                TrekkAlternativ.valueOf(row.string(TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN).uppercase())
            }.toSet()

    fun <A> withTransaction(operation: (TransactionalSession) -> A): A = dataSource.withTransaction(operation)

    fun countUtleggstrekk(): Map<Trekkstatus, Long> =
        dataSource.withTransaction { session ->
            session
                .list(
                    queryOf(
                        """
                        SELECT trekkstatus, COUNT(*) as count
                            FROM (SELECT DISTINCT ON (trekkid) trekkid, trekkstatus 
                                FROM fraskatt ORDER BY trekkid, trekkversjon DESC) 
                            t GROUP BY trekkstatus
                        """.trimIndent(),
                    ),
                ) { row -> Trekkstatus.valueOf(row.string("trekkstatus")) to row.long("count") }
                .toMap()
        }

    fun countKvitterteTrekkTilOS(): Map<TrekkAlternativ, Long> =
        dataSource.withTransaction { session ->
            session
                .list(
                    queryOf(
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
                """
                    |SELECT * FROM fraskatt
                    |WHERE trekkid=:trekkid ORDER BY trekk DESC LIMIT 1
                """.trimMargin(),
                mapOf("trekkid" to trekkId),
            ),
        ) { row ->
            TrekkFraSkatt(row)
        } ?: throw IllegalStateException("Fant ikke trekk fra skatt for $trekkId")

    fun getTrekkIdTilTrekkSomSkalBehandles(): List<Long> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """
                    SELECT f.id FROM fraskatt f
                    LEFT JOIN  fraskatt_status t ON t.fraskatt_id = f.id
                    WHERE t.status IS NULL 
                    OR t.status IN ('${SkattTrekkStatus.MOTTATT.name}', '${SkattTrekkStatus.REPETERES.name}')
                    ORDER BY f.sekvensnummer ASC
                    """.trimIndent(),
                ),
            ) { row -> row.long(1) }
        }
}

fun <A> HikariDataSource.withTransaction(operation: (TransactionalSession) -> A): A =
    using(sessionOf(this, returnGeneratedKey = true)) { session ->
        session.transaction { tx ->
            operation(tx)
        }
    }