package com.backend.clients

import com.backend.exceptions.BggRequestFailedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

@Component
class BggScraperClient(
    @Qualifier("bggScraperWebClient") private val bggScraperWebClient: WebClient
) {
    private val logger = LoggerFactory.getLogger(BggScraperClient::class.java)

    fun downloadRankExportZip(): ByteArray {
        try {
            logger.debug("[bgg_csv_client][download_rank_export_zip] Warming up BGG session...")

            // Step 1: Hit sulla homepage per acquisire la cookie session di Cloudflare/BGG
            bggScraperWebClient.get()
                .uri("/")
                .retrieve()
                .bodyToMono<String>()
                .timeout(Duration.ofSeconds(15))
                .onErrorReturn("")
                .block()

            logger.debug("[bgg_csv_client][download_rank_export_zip] Requesting bg_ranks.zip data dump...")

            // Step 2: Download effettivo aggiungendo l'header Referer
            val zipBytes = bggScraperWebClient.get()
                .uri("/data_dumps/bg_ranks.zip")
                .header("Referer", "https://boardgamegeek.com/")
                .retrieve()
                .bodyToMono<ByteArray>()
                .timeout(Duration.ofSeconds(60))
                .block() ?: throw RuntimeException("Empty response body received from BGG dump")

            logger.info("[bgg_csv_client][download_rank_export_zip] Successfully downloaded zip file ({} bytes)", zipBytes.size)
            return zipBytes

        } catch (e: Exception) {
            logger.error("[bgg_csv_client][download_rank_export_zip] Failed to download rank export zip: {}", e.message)
            throw BggRequestFailedException(e)
        }
    }
}