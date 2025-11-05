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
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.service.withTransaction

class RepositoryNy(private val dataSource: HikariDataSource) {
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

    fun insertTrekkFraSkatt(trekkpaalegg: Trekkpaalegg): Long? =
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
                        "status" to SkattTrekkStatus.MOTTATT.name,
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

    object TransaksjonOsTable {
        const val TABLE_NAME = "transaksjon_os"
        const val ID_COLUMN = "id"
        const val NAV_TREKK_ID_PARAM = "navTrekkId"
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
        const val DOKUMENT_JSON_PARAM = "dokumentJson"
        const val PRIORITET_FOM_DATO_PARAM = "prioritetFomDato"
        const val GYLDIG_TOM_DATO_PARAM = "gyldigTomDato"

        const val NAV_TREKK_ID_COLUMN = "nav_trekk_id"
        const val TRANSAKSJONS_ID_COLUMN = "transaksjons_id"
        const val TRANSAKSJON_STATUS_COLUMN = "transaksjon_status"
        const val TREKK_ID_SKE_COLUMN = "trekk_id_ske"
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

    fun insertTransaksjonTilOs(dto: OSDto) {
        dataSource.withTransaction { session ->
            val id =
                session.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                          INSERT INTO 
                        ${TransaksjonOsTable.TABLE_NAME} (
                              ${TransaksjonOsTable.TRANSAKSJONS_ID_COLUMN}, 
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
                               ${TransaksjonOsTable.DOKUMENT_JSON_COLUMN}, 
                               ${TransaksjonOsTable.PRIORITET_FOM_DATO_COLUMN},
                               ${TransaksjonOsTable.GYLDIG_TOM_DATO_COLUMN}
                          ) VALUES(
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
                              :${TransaksjonOsTable.DOKUMENT_JSON_PARAM},
                              :${TransaksjonOsTable.PRIORITET_FOM_DATO_PARAM},
                              :${TransaksjonOsTable.GYLDIG_TOM_DATO_PARAM}
                          )
                        """.trimIndent(),
                        mapOf(
                            TransaksjonOsTable.TRANSAKSJONS_ID_PARAM to dto.transaksjonID,
                            TransaksjonOsTable.TRANSAKSJON_STATUS_PARAM to TransaksjonsStatus.IKKE_SENDT.name,
                            TransaksjonOsTable.TREKK_ID_SKE_PARAM to dto.trekkIDSke,
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
            dto.innrapporteringTrekk.perioder.periode.forEach { periode ->
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
    }

    fun updateTransaksjonStatus(transaksjonId: String, transaksjonStatus: TransaksjonsStatus) {
        dataSource.withTransaction { session ->
            session.update(
                queryOf(
                    """
                     UPDATE  ${TransaksjonOsTable.TABLE_NAME}  
                     SET 
                    ${TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN}=:${TransaksjonOsTable.TRANSAKSJON_STATUS_PARAM},
                     ${TransaksjonOsTable.TIDSPUNKT_SISTE_STATUS_COLUMN}=NOW() 
                     WHERE ${TransaksjonOsTable.TRANSAKSJONS_ID_COLUMN}=:${TransaksjonOsTable.TRANSAKSJONS_ID_PARAM}
                    """.trimIndent(),
                    mapOf(
                        TransaksjonOsTable.TRANSAKSJON_STATUS_PARAM to transaksjonStatus.name,
                        TransaksjonOsTable.TRANSAKSJONS_ID_PARAM to transaksjonId,
                    ),
                ),
            )
        }
    }

    // TODO: Rename?
    fun updateTransaksjon(transaksjonId: String, kvitteringStatus: KvitteringStatus, navTrekkId: String) {
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

    fun updateTrekkFraSkattStatus(fraSkattId: Long, status: SkattTrekkStatus) {
        dataSource.withTransaction { session ->
            session.update(
                queryOf(
                    "UPDATE fraskatt_status SET status = :status, tidspunkt_satt = NOW() WHERE id = :id",
                    mapOf("id" to fraSkattId, "status" to status.name),
                ),
            )
        }
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

    fun getTrekkFraSkatt(id: String): List<TrekkFraSkatt> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """SELECT * FROM fraskatt WHERE trekkid=:id""".trimIndent(),
                    mapOf("id" to id),
                ),
            ) { row -> TrekkFraSkatt(row) }
        }

    fun getAllePerioderForTrekkId(trekkIdSke: String): List<PeriodeFraSkatt> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """SELECT * FROM periode WHERE trekk_id_ske=:trekkIdSke""".trimIndent(),
                    mapOf("trekkIdSke" to trekkIdSke),
                ),
            ) { row -> PeriodeFraSkatt(row) }
        }

    fun getPerioderForTrekkVersjon(fraSkattId: Long, sekvensnummer: Int, trekkversjon: Int): List<PeriodeFraSkatt> =
        dataSource.withTransaction { session ->
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
        }

    fun getPerioderForTrekk(trekkFraSkatt: TrekkFraSkatt): List<PeriodeFraSkatt> = getPerioderForTrekkVersjon(trekkFraSkatt.id, trekkFraSkatt.sekvensnummer, trekkFraSkatt.trekkversjon)

    fun getTrekkAlternativOS(trekkIdSke: String): List<TrekkAlternativ> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """
                        SELECT DISTINCT ${TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN}
                        FROM  ${TransaksjonOsTable.TABLE_NAME}  
                        WHERE ${TransaksjonOsTable.TREKK_ID_SKE_COLUMN}=:${TransaksjonOsTable.TREKK_ID_SKE_PARAM}
                        AND ${TransaksjonOsTable.KVITTERING_STATUS_COLUMN} = '${KvitteringStatus.OK}' 
                        OR ${TransaksjonOsTable.KVITTERING_STATUS_COLUMN}  = '${KvitteringStatus.IKKE_MOTTATT.name}'
                    """.trimMargin(),
                    mapOf(TransaksjonOsTable.TREKK_ID_SKE_PARAM to trekkIdSke),
                ),
            ) { row -> TrekkAlternativ.valueOf(row.string(TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN).uppercase()) }
        }

    fun insertPeriodeTilOs(transaksjonOSId: Int, periodeTilOs: PeriodeTilOS) {
        dataSource.withTransaction { session ->
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
                        AND t.kvittering_status in ('${KvitteringStatus.IKKE_MOTTATT.name}', '${KvitteringStatus.OK.name}')
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

    // TODO: Bruke fraskatt_status? Må også oppdatere hvordan ostransaksjon funker
    fun getTrekkSomIkkeErBehandlet(): List<TrekkFraSkatt> =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """
                    SELECT f.* FROM fraskatt f
                    LEFT JOIN  fraskatt_status t ON t.fraskatt_id = f.id
                    WHERE t.status IS NULL OR t.status != 'BEHANDLET'
                    """.trimIndent(),
                ),
            ) { row -> TrekkFraSkatt(row) }
        }

    fun getTransaksjonerTilOsSomIkkeErSendt() =
        dataSource.withTransaction { session ->
            session.list(
                queryOf(
                    """
                    SELECT * FROM  ${TransaksjonOsTable.TABLE_NAME}  WHERE ${TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN} IS null OR ${TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN}  = '${TransaksjonsStatus.IKKE_SENDT.name}'
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

    fun getOsAlternativForTrekk(trekk: TrekkFraSkatt): Set<TrekkAlternativ> =
        dataSource.withTransaction { session ->
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
        }
}