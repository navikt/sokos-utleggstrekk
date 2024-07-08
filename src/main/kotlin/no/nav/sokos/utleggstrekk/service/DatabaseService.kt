package no.nav.sokos.utleggstrekk.service

import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.PostgresDataSource

private val logger = KotlinLogging.logger {  }
class DatabaseService(
    private val dataSource: PostgresDataSource
) {}