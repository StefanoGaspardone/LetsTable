package com.backend.clients

import com.backend.exceptions.BggRequestFailedException
import com.backend.models.dtos.BggHotResponseXml
import com.backend.models.dtos.BggSearchResponseXml
import com.backend.models.dtos.BggThingResponseXml
import com.backend.models.dtos.CardSetsByGameResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.net.URI
import java.time.Duration

@Component
class BggClient(
    private val bggWebClient: WebClient,
    @Value($$"${bgg.api-token}") private val apiToken: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val xmlMapper = XmlMapper().registerKotlinModule()
    private val jsonMapper = ObjectMapper().registerKotlinModule()

    fun searchGames(query: String): BggSearchResponseXml {
        logger.debug("\n\t[DEBUG] [bgg_client][search_games] Searching BGG for query {}", query)

        try {
            val rawXml = bggWebClient.get()
                .uri { it.path("/search").queryParam("query", query).queryParam("type", "boardgame").build() }
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiToken")
                .retrieve()
                .bodyToMono<String>()
                .timeout(Duration.ofSeconds(10))
                .block()!!

            val parsed = xmlMapper.readValue(rawXml, BggSearchResponseXml::class.java)

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
            val rawXml = bggWebClient.get()
                .uri { it.path("/thing").queryParam("id", bggId).queryParam("stats", "1").build() }
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiToken")
                .retrieve()
                .bodyToMono<String>()
                .timeout(Duration.ofSeconds(10))
                .block()!!

            val parsed = xmlMapper.readValue(rawXml, BggThingResponseXml::class.java)

            logger.info("\n\t[INFO] [bgg_client][get_game_details] Fetched details for id {}", bggId)
            return parsed
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [bgg_client][get_game_details] Error fetching BGG details for id {}: {}", bggId, e.message)
            throw BggRequestFailedException(e)
        }
    }

    fun getGameDetailsBatch(bggIds: List<Long>): BggThingResponseXml {
        logger.debug("\n\t[DEBUG] [bgg_client][get_game_details_batch] Fetching BGG details for {} ids", bggIds.size)

        if(bggIds.isEmpty()) return BggThingResponseXml(items = emptyList())

        try {
            val allItems = bggIds.chunked(20).flatMap { chunk ->
                val idsParam = chunk.joinToString(",")
                val rawXml = bggWebClient.get()
                    .uri { it.path("/thing").queryParam("id", idsParam).queryParam("stats", "1").build() }
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $apiToken")
                    .retrieve()
                    .bodyToMono<String>()
                    .timeout(Duration.ofSeconds(15))
                    .block()!!

                xmlMapper.readValue(rawXml, BggThingResponseXml::class.java).items
            }

            logger.info("\n\t[INFO] [bgg_client][get_game_details_batch] Fetched details for {} of {} requested ids", allItems.size, bggIds.size)
            return BggThingResponseXml(items = allItems)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [bgg_client][get_game_details_batch] Error fetching BGG batch details: {}", e.message)
            throw BggRequestFailedException(e)
        }
    }

    fun getHotGames(): BggHotResponseXml {
        logger.debug("\n\t[DEBUG] [bgg_client][get_hot_games] Fetching BGG hot list")

        try {
            val rawXml = bggWebClient.get()
                .uri { it.path("/hot").queryParam("type", "boardgame").build() }
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiToken")
                .retrieve()
                .bodyToMono<String>()
                .timeout(Duration.ofSeconds(10))
                .block()!!

            val parsed = xmlMapper.readValue(rawXml, BggHotResponseXml::class.java)

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
            val uri = URI.create("https://api.geekdo.com/api/cardsetsbygame?objectid=$bggId&nosession=1")

            val rawJson = bggWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono<String>()
                .timeout(Duration.ofSeconds(10))
                .block()!!

            val parsed = jsonMapper.readValue(rawJson, CardSetsByGameResponse::class.java)

            logger.info("\n\t[INFO] [bgg_client][get_card_sets_by_game] Fetched {} card sets for id {}", parsed.cardSets.size, bggId)
            return parsed
        } catch(e: Exception) {
            logger.warn("\n\t[WARN] [bgg_client][get_card_sets_by_game] Error fetching card sets for id {}: {}", bggId, e.message)
            return CardSetsByGameResponse()
        }
    }
}