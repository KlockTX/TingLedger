package com.klocktx.moneyblocks.data.repo

import com.klocktx.moneyblocks.data.local.ExportDao
import com.klocktx.moneyblocks.domain.repo.ExportRepository
import com.klocktx.moneyblocks.domain.usecase.CsvExport
import java.time.ZoneId

class ExportRepositoryImpl(private val dao: ExportDao) : ExportRepository {
    override suspend fun exportCsv(zoneId: ZoneId): String = CsvExport.build(dao.exportRows(), zoneId)
}
