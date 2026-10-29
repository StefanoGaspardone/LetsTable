package com.backend.clients

import com.backend.exceptions.BggRequestFailedException
import com.backend.models.dtos.BggHotResponseXml
import com.backend.models.dtos.BggSearchResponseXml
import com.backend.models.dtos.BggThingResponseXml
import com.backend.models.dtos.CardSetsByGameResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.kotlin.KotlinModule
import java.net.URI
import java.time.Duration

@Component
class BggClient(
    private val bggWebClient: WebClient,
    @Value($$"${bgg.api-token}") private val apiToken: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val xmlMapper = XmlMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    companion object {
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)
        private const val BATCH_SIZE = 20
        private const val CARD_SETS_URL = "https://api.geekdo.com/api/cardsetsbygame"
    }

    fun searchGames(query: String): BggSearchResponseXml {
        logger.debug("\n\t[DEBUG] [bgg_client][search_games] Searching BGG for query {}", query)

        try {
            val parsed = fetchXml("/search", mapOf("query" to query, "type" to "boardgame"), BggSearchResponseXml::class.java)

            logger.info("\n\t[INFO] [bgg_client][search_games] Found {} results for query {}", parsed.items.size, query)
            return parsed
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [bgg_client][search_games] Error searching BGG for query {}: {}", query, e.message)
            throw BggRequestFailedException(e)
        }
    }

    fun getGameDetails(bggId: Long): BggThingResponseXml {
        logger.debug("\n\t[DEBUG] [bgg_client][get_game_details] Fetching BGG details for id {}", bggId)

        try {
            val parsed = fetchXml("/thing", mapOf("id" to bggId.toString(), "stats" to "1"), BggThingResponseXml::class.java)

            logger.info("\n\t[INFO] [bgg_client][get_game_details] Fetched details for id {}", bggId)
            return parsed
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [bgg_client][get_game_details] Error fetching BGG details for id {}: {}", bggId, e.message)
            throw BggRequestFailedException(e)
        }
    }

    fun getGameDetailsBatch(bggIds: List<Long>): BggThingResponseXml {
        logger.debug("\n\t[DEBUG] [bgg_client][get_game_details_batch] Fetching BGG details for {} ids", bggIds.size)

        if(bggIds.isEmpty()) {
            return BggThingResponseXml(items = emptyList())
        }

        try {
            val items = bggIds.chunked(BATCH_SIZE).flatMap { chunk ->
                fetchXml("/thing", mapOf("id" to chunk.joinToString(","), "stats" to "1"), BggThingResponseXml::class.java).items
            }

            logger.info("\n\t[INFO] [bgg_client][get_game_details_batch] Fetched details for {} of {} requested ids", items.size, bggIds.size)
            return BggThingResponseXml(items = items)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [bgg_client][get_game_details_batch] Error fetching BGG batch details: {}", e.message)
            throw BggRequestFailedException(e)
        }
    }

    fun getHotGames(): BggHotResponseXml {
        logger.debug("\n\t[DEBUG] [bgg_client][get_hot_games] Fetching BGG hot list")

        try {
            val parsed = fetchXml("/hot", mapOf("type" to "boardgame"), BggHotResponseXml::class.java)

            logger.info("\n\t[INFO] [bgg_client][get_hot_games] Fetched {} hot games", parsed.items.size)
            return parsed
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [bgg_client][get_hot_games] Error fetching BGG hot list: {}", e.message)
            throw BggRequestFailedException(e)
        }
    }

    fun getCardSetsByGame(bggId: Long): CardSetsByGameResponse {
        logger.debug("\n\t[DEBUG] [bgg_client][get_card_sets_by_game] Fetching card sets for id {}", bggId)

        try {
            val rawJson = bggWebClient.get()
                .uri(URI.create("$CARD_SETS_URL?objectid=$bggId&nosession=1"))
                .retrieve()
                .bodyToMono<String>()
                .timeout(REQUEST_TIMEOUT)
                .block()
                ?: return CardSetsByGameResponse()

            val parsed = jsonMapper.readValue(rawJson, CardSetsByGameResponse::class.java)

            logger.info("\n\t[INFO] [bgg_client][get_card_sets_by_game] Fetched {} card sets for id {}", parsed.cardSets.size, bggId)
            return parsed
        } catch(e: Exception) {
            logger.warn("\n\t[WARN] [bgg_client][get_card_sets_by_game] Error fetching card sets for id {}: {}", bggId, e.message)
            return CardSetsByGameResponse()
        }
    }

    private fun <T> fetchXml(path: String, params: Map<String, String>, type: Class<T>): T {
        val rawXml = bggWebClient.get()
            .uri { builder ->
                builder.path(path)
                params.forEach { (name, value) -> builder.queryParam(name, value) }
                builder.build()
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiToken")
            .retrieve()
            .bodyToMono<String>()
            .timeout(REQUEST_TIMEOUT)
            .block()
            ?: throw IllegalStateException("Empty response from BGG $path")

        return xmlMapper.readValue(rawXml, type)
    }
}