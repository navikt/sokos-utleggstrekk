package sokos.utleggstrekk.service

import mu.KotlinLogging
import sokos.utleggstrekk.database.OracleDataSource

private val logger = KotlinLogging.logger {  }
class DatabaseService(
    private val dataSource: OracleDataSource
) {}