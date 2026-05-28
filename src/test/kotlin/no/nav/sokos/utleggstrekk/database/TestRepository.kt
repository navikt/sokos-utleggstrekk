package no.nav.sokos.utleggstrekk.database

import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.database.model.Feilmelding
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.OSDto

object TestRepository {
    fun Repository.insertTransaksjonTilOs(dto: OSDto) = withTransaction { session -> insertTransaksjonTilOs(dto, session) }

    fun Repository.getTransaksjonTilOs(transaksjonsId: String): TransaksjonOS? =
        withTransaction { session ->
            session.single(
                queryOf(
                    // language=SQL
                    """
                    SELECT * FROM  transaksjon_os  WHERE 
                    transaksjons_id=:transaksjonsId
                    """.trimIndent(),
                    mapOf("transaksjonsId" to transaksjonsId),
                ),
            ) { row ->
                val transaksjonId = row.long("id")
                val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

                TransaksjonOS(row, perioderTilOS)
            }
        }

    fun Repository.getAllTransaksjonerTilOs(): List<TransaksjonOS> =
        withTransaction { session ->
            session.list(
                queryOf(
                    // language=SQL
                    """
                    SELECT * FROM  transaksjon_os 
                    """.trimIndent(),
                ),
            ) { row ->
                val transaksjonId = row.long("id")
                val perioderTilOS = getPerioderForTransaksjon(transaksjonId, session)

                TransaksjonOS(row, perioderTilOS)
            }
        }

    fun Repository.getTrekkFraSkatt(id: Long): TrekkFraSkatt =
        withTransaction { session ->
            getTrekkFraSkatt(id, session)
        }

    fun Repository.doesTrekkExist(trekkId: String, trekkversjon: Int): Boolean =
        withTransaction { session ->
            session.single(
                queryOf(
                    // language=SQL
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

    fun Repository.getTrekkFraSkattMedStatus(status: SkattTrekkStatus): List<TrekkFraSkatt> =
        withTransaction { session ->
            session.list(
                queryOf(
                    // language=SQL
                    "SELECT * FROM fraskatt f JOIN fraskatt_status s ON f.id = s.fraskatt_id WHERE s.status=:status ORDER BY f.sekvensnummer ASC",
                    mapOf("status" to status.name),
                ),
            ) { row ->
                TrekkFraSkatt(row)
            }
        }

    fun Repository.getTrekkFraSkattStatus(id: Long): SkattTrekkStatus? =
        withTransaction { session ->
            session.single(
                queryOf(
                    // language=SQL
                    "SELECT status FROM fraskatt f JOIN fraskatt_status s ON f.id = s.fraskatt_id WHERE f.id=:id",
                    mapOf("id" to id),
                ),
            ) { row ->
                SkattTrekkStatus.valueOf(row.string("status"))
            }
        }

    fun Repository.getFeilmeldingerFraOS(transaksjonsId: String): Feilmelding? =
        withTransaction { session ->
            session.single(
                queryOf(
                    // language=SQL
                    "SELECT * FROM feilmelding WHERE transaksjons_id=:transaksjonsId",
                    mapOf("transaksjonsId" to transaksjonsId),
                ),
            ) { row -> Feilmelding(row) }
        }

    fun Repository.getAllTrekkFraSkatt(): List<TrekkFraSkatt> =
        withTransaction { session ->
            session.list(
                queryOf(
                    // language=SQL
                    """SELECT * FROM fraskatt""".trimIndent(),
                ),
            ) { row -> TrekkFraSkatt(row) }
        }

    fun Repository.getTrekkFraSkatt(trekkid: String, versjon: Int): TrekkFraSkatt? =
        withTransaction { session ->
            session.single(
                queryOf(
                    // language=SQL
                    """SELECT * FROM fraskatt WHERE trekkid=:trekkid AND trekkversjon=:versjon""".trimIndent(),
                    mapOf("trekkid" to trekkid, "versjon" to versjon),
                ),
            ) { row -> TrekkFraSkatt(row) }
        }
}