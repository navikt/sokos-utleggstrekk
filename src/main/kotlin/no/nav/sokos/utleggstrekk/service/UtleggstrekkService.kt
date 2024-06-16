package no.nav.sokos.utleggstrekk.service

import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.service.DatabaseService

private val logger = KotlinLogging.logger {  }
class UtleggstrekkService(
    private val databaseService: DatabaseService
) {}