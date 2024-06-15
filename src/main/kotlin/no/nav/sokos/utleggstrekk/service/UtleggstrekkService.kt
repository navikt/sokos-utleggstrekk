package sokos.utleggstrekk.service

import mu.KotlinLogging

private val logger = KotlinLogging.logger {  }
class UtleggstrekkService(
    private val databaseService: DatabaseService
) {}