package com.backend.services

import com.backend.exceptions.InvalidRankIndexFileException
import com.backend.models.dtos.BggRankIndexDTO
import com.backend.models.entities.BggRankIndex
import com.backend.repositories.BggRankIndexRepository
import org.apache.commons.csv.CSVFormat
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader

@Service
class BggRankIndexService(
    private val bggRankIndexRepository: BggRankIndexRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun uploadRankIndex(file: MultipartFile): BggRankIndexDTO {
        logger.debug("\n\t[DEBUG] [bgg_rank_index_service][upload_rank_index] Processing uploaded rank index file\n\tfilename={}\n\tsize={}", file.originalFilename, file.size)

        try {
            val entries = parseCsv(file)

            if(entries.isEmpty()) {
                throw InvalidRankIndexFileException("No valid rank entries found in uploaded file")
            }

            bggRankIndexRepository.deleteAllInBatch()
            bggRankIndexRepository.saveAll(entries)

            logger.info("\n\t[INFO] [bgg_rank_index_service][upload_rank_index] Rank index refreshed with {} entries", entries.size)
            return BggRankIndexDTO(count = entries.size.toLong())
        } catch(e: InvalidRankIndexFileException) {
            logger.warn("\n\t[WARN] [bgg_rank_index_service][upload_rank_index] Invalid rank index file: {}", e.message)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [bgg_rank_index_service][upload_rank_index] Error processing rank index file: {}", e.message, e)
            throw InvalidRankIndexFileException("Could not parse the uploaded file: ${e.message}")
        }
    }

    fun getRankIndexCount(): Long =
        bggRankIndexRepository.count()

    private fun parseCsv(file: MultipartFile): List<BggRankIndex> {
        val entries = mutableListOf<BggRankIndex>()

        val reader = BufferedReader(InputStreamReader(file.inputStream))
        val csvParser = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build()
            .parse(reader)

        for(record in csvParser) {
            val bggId = record.get("id")?.toLongOrNull() ?: continue
            val rank = record.get("rank")?.toIntOrNull() ?: continue

            if(rank > 0) {
                entries.add(BggRankIndex(bggId = bggId, rank = rank))
            }
        }

        return entries
    }
}