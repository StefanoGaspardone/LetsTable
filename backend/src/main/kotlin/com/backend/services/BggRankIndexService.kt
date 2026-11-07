package com.backend.services

import com.backend.exceptions.InvalidRankIndexFileException
import com.backend.models.dtos.BggRankIndexDTO
import com.backend.models.entities.BggRankIndex
import com.backend.repositories.BggRankIndexRepository
import jakarta.persistence.EntityManager
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets

@Service
class BggRankIndexService(
    private val bggRankIndexRepository: BggRankIndexRepository,
    private val entityManager: EntityManager,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ID_COLUMN = "id"
        private const val RANK_COLUMN = "rank"
        private const val CSV_EXTENSION = ".csv"
        private const val INSERT_BATCH_SIZE = 500
        private const val BYTE_ORDER_MARK = 0xFEFF
    }

    @Transactional
    fun uploadRankIndex(file: MultipartFile): BggRankIndexDTO {
        logger.debug("\n\t[DEBUG] [bgg_rank_index_service][upload_rank_index] Processing uploaded rank index file\n\tfilename={}\n\tsize={}", file.originalFilename, file.size)

        try {
            if(file.originalFilename?.endsWith(CSV_EXTENSION, ignoreCase = true) != true) {
                throw InvalidRankIndexFileException("Only CSV files are allowed")
            }

            val entries = parseCsv(file)

            if(entries.isEmpty()) {
                throw InvalidRankIndexFileException("No valid rank entries found in uploaded file")
            }

            bggRankIndexRepository.deleteAllInBatch()

            entries.chunked(INSERT_BATCH_SIZE).forEach { chunk ->
                chunk.forEach { entityManager.persist(it) }
                entityManager.flush()
                entityManager.clear()
            }

            logger.info("\n\t[INFO] [bgg_rank_index_service][upload_rank_index] Rank index refreshed with {} entries", entries.size)
            return BggRankIndexDTO(count = entries.size.toLong())
        } catch(e: InvalidRankIndexFileException) {
            logger.warn("\n\t[WARN] [bgg_rank_index_service][upload_rank_index] Invalid rank index file: {}", e.message)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [bgg_rank_index_service][upload_rank_index] Error processing rank index file: {}", e.message, e)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getRankIndexCount(): Long =
        bggRankIndexRepository.count()

    private fun parseCsv(file: MultipartFile): List<BggRankIndex> {
        try {
            BufferedReader(InputStreamReader(file.inputStream, StandardCharsets.UTF_8)).use { reader ->
                skipByteOrderMark(reader)

                val parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build()
                    .parse(reader)

                listOf(ID_COLUMN, RANK_COLUMN)
                    .firstOrNull { column -> parser.headerMap.keys.none { it.equals(column, ignoreCase = true) } }
                    ?.let { throw InvalidRankIndexFileException("Missing required column: $it") }

                return parser
                    .mapNotNull { record ->
                        val bggId = record.valueOf(ID_COLUMN)?.toLongOrNull()
                        val rank = record.valueOf(RANK_COLUMN)?.toIntOrNull()

                        if(bggId != null && bggId > 0 && rank != null && rank > 0) BggRankIndex(bggId = bggId, rank = rank) else null
                    }
                    .distinctBy { it.bggId }
            }
        } catch(e: IOException) {
            throw InvalidRankIndexFileException("Could not read the uploaded file: ${e.message}")
        } catch(e: UncheckedIOException) {
            throw InvalidRankIndexFileException("Could not parse the uploaded file: ${e.message}")
        } catch(e: IllegalArgumentException) {
            throw InvalidRankIndexFileException("Could not parse the uploaded file: ${e.message}")
        }
    }

    private fun skipByteOrderMark(reader: BufferedReader) {
        reader.mark(1)

        if(reader.read() != BYTE_ORDER_MARK) {
            reader.reset()
        }
    }

    private fun CSVRecord.valueOf(column: String): String? =
        if(isSet(column)) get(column) else null
}