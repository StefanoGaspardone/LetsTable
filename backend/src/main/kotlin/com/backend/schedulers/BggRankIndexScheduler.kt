package com.backend.schedulers

import com.backend.clients.BggScraperClient
import com.backend.models.entities.BggRankIndex
import com.backend.repositories.BggRankIndexRepository
import org.apache.commons.csv.CSVFormat
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

@Service
class BggRankIndexScheduler(
    private val bggScraperClient: BggScraperClient,
    private val bggRankIndexRepository: BggRankIndexRepository,
    @Lazy private val self: BggRankIndexScheduler?,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun refreshOnStartup() {
        if (bggRankIndexRepository.count() > 0) {
            logger.info("\n\t[INFO] [bgg_rank_index_service][refresh_on_startup] Rank index already populated, skipping startup refresh")
            return
        }

        logger.info("\n\t[INFO] [bgg_rank_index_service][refresh_rank_index] Rank index empty, triggering initial refresh")
        self?.refreshRankIndex()
    }

    @Scheduled(cron = "0 0 3 * * MON")
    @Transactional
    fun refreshRankIndex() {
        logger.debug("\n\t[DEBUG] [bgg_rank_index_service][refresh_rank_index] Starting weekly rank index refresh")

        try {
            val zipBytes = bggScraperClient.downloadRankExportZip()
            val entries = parseCsvFromZip(zipBytes)

            bggRankIndexRepository.deleteAllInBatch()
            bggRankIndexRepository.saveAll(entries)

            logger.info("\n\t[INFO] [bgg_rank_index_service][refresh_rank_index] Rank index refreshed with {} entries", entries.size)
        } catch (e: Exception) {
            logger.error("\n\t[ERROR] [bgg_rank_index_service][refresh_rank_index] Error refreshing rank index: {}", e.message, e)
        }
    }

    private fun parseCsvFromZip(zipBytes: ByteArray): List<BggRankIndex> {
        val entries = mutableListOf<BggRankIndex>()

        ZipInputStream(zipBytes.inputStream()).use { zipStream ->
            var zipEntry = zipStream.nextEntry

            while (zipEntry != null) {
                if (zipEntry.name.endsWith(".csv")) {
                    val reader = BufferedReader(InputStreamReader(zipStream))
                    val csvParser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build()
                        .parse(reader)

                    for (record in csvParser) {
                        val bggId = record.get("id")?.toLongOrNull() ?: continue
                        val rank = record.get("rank")?.toIntOrNull() ?: continue

                        if (rank > 0) {
                            entries.add(BggRankIndex(bggId = bggId, rank = rank))
                        }
                    }

                    break
                }

                zipEntry = zipStream.nextEntry
            }
        }

        return entries
    }
}