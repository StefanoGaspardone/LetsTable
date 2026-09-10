package com.backend.seeders

import com.backend.clients.BggClient
import com.backend.models.dtos.BggThingItemXml
import com.backend.models.entities.Game
import com.backend.repositories.GameRepository
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class GameSeeder(
    private val gameRepository: GameRepository,
    private val bggClient: BggClient,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val seedBggIds = listOf(
        342942L,
        397598L,
        421006L,
        233078L
    )

    fun seed(): List<Game> {
        logger.debug("\n\t[DEBUG] [game_seeder][seed] Seeding demo games and their expansions from BGG")

        return try {
            val existingGamesMap = gameRepository
                .findAllByBggIdIn(seedBggIds)
                .associateBy { it.bggId }

            val missingBggIds = seedBggIds.filter { id -> !existingGamesMap.containsKey(id) }

            if(missingBggIds.isNotEmpty()) {
                logger.info("\n\t[INFO] [game_seeder][seed] Fetching details and expansions for missing games: {}", missingBggIds)

                val baseGamesXml = bggClient.getGameDetailsBatch(missingBggIds)
                val allExpansionIds = baseGamesXml.items
                    .flatMap { it.expansionRefs() }
                    .map { it.bggId }
                    .distinct()

                val expansionToBaseGameIdMap = baseGamesXml.items.flatMap { baseItem ->
                    baseItem.expansionRefs().map { expRef -> expRef.bggId to baseItem.id }
                }.toMap()

                val expansionsXml = if(allExpansionIds.isNotEmpty()) {
                    logger.info("\n\t[INFO] [game_seeder][seed] Fetching {} total expansions in batch...", allExpansionIds.size)
                    bggClient.getGameDetailsBatch(allExpansionIds)
                } else {
                    null
                }

                val baseGameEntities = baseGamesXml.items.map { item ->
                    val game = gameRepository.findByBggId(item.id).orElseGet { Game(bggId = item.id, name = "") }
                    applyBggDetails(game, item)
                }
                gameRepository.saveAll(baseGameEntities)

                expansionsXml?.items?.let { expItems ->
                    val expansionEntities = expItems.map { expItem ->
                        val expGame = gameRepository.findByBggId(expItem.id).orElseGet { Game(bggId = expItem.id, name = "") }
                        applyBggDetails(expGame, expItem)

                        expGame.isExpansion = true
                        expGame.baseGameBggId = expansionToBaseGameIdMap[expItem.id] ?: expItem.baseGameRef()?.bggId
                        expGame
                    }
                    gameRepository.saveAll(expansionEntities)
                    logger.info("\n\t[INFO] [game_seeder][seed] Successfully saved {} expansions", expansionEntities.size)
                }
            } else {
                logger.info("\n\t[INFO] [game_seeder][seed] All seed games already present in DB")
            }

            val allSeedGames = gameRepository.findAllByBggIdIn(seedBggIds)

            logger.info("\n\t[INFO] [game_seeder][seed] {} demo base games ready", allSeedGames.size)
            allSeedGames
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_seeder][seed] Error seeding games from BGG: {}. Falling back to static mock seed.", e.message)
            seedStaticFallback()
        }
    }

    private fun applyBggDetails(game: Game, details: BggThingItemXml): Game {
        val cleanDescription = details.description
            ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            ?.let { Jsoup.parse(it).body().wholeText() }
            ?.replace(Regex("""[—–-]\s*description from (the )?(publisher|designer|artist|manufacturer)\.?\s*$""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("\n{3,}"), "\n\n")
            ?.trim()

        game.name = details.primaryName() ?: game.name
        game.yearPublished = details.yearPublished?.value?.toIntOrNull()
        game.thumbnailUrl = details.thumbnail
        game.imageUrl = details.image
        game.minPlayers = details.minPlayers?.value?.toIntOrNull()?.takeIf { it > 0 }
        game.maxPlayers = details.maxPlayers?.value?.toIntOrNull()?.takeIf { it > 0 }
        game.playingTimeMinutes = details.playingTime?.value?.toIntOrNull()?.takeIf { it > 0 }
        game.description = cleanDescription
        game.lastSyncedAt = Instant.now()
        game.isExpansion = details.type != "boardgame"

        val baseGameRef = details.baseGameRef()
        game.baseGameBggId = baseGameRef?.bggId
        game.difficulty = details.statistics
            ?.ratings
            ?.averageWeight
            ?.value
            ?.toDoubleOrNull()
            ?.takeIf { it > 0 }

        game.designers = details.links.filter { it.type == "boardgamedesigner" }.map { it.value }
        game.artists = details.links.filter { it.type == "boardgameartist" }.map { it.value }
        game.publishers = details.links.filter { it.type == "boardgamepublisher" }.map { it.value }

        return game
    }

    private fun seedStaticFallback(): List<Game> {
        val staticSpecs = listOf(
            Game(
                bggId = 342942L,
                name = "Ark Nova",
                yearPublished = 2021,
                thumbnailUrl = "https://picsum.photos/seed/ark-nova/200",
                imageUrl = "https://picsum.photos/seed/ark-nova/600",
                minPlayers = 1,
                maxPlayers = 4,
                playingTimeMinutes = 150,
                description = "Costruisci e gestisci uno zoo moderno orientato alla conservazione delle specie.",
                lastSyncedAt = Instant.now(),
            ),
            Game(
                bggId = 397598L,
                name = "SETI: Search for Extraterrestrial Intelligence",
                yearPublished = 2024,
                thumbnailUrl = "https://picsum.photos/seed/seti/200",
                imageUrl = "https://picsum.photos/seed/seti/600",
                minPlayers = 1,
                maxPlayers = 4,
                playingTimeMinutes = 180,
                description = "Esplora il sistema solare e ricerca segnali di vita extraterrestre.",
                lastSyncedAt = Instant.now(),
            ),
            Game(
                bggId = 421006L,
                name = "Dune: Uprising",
                yearPublished = 2025,
                thumbnailUrl = "https://picsum.photos/seed/dune-uprising/200",
                imageUrl = "https://picsum.photos/seed/dune-uprising/600",
                minPlayers = 2,
                maxPlayers = 4,
                playingTimeMinutes = 90,
                description = "Gioco di conflitto asimmetrico ambientato nell'universo di Dune.",
                lastSyncedAt = Instant.now(),
            ),
            Game(
                bggId = 233078L,
                name = "Twilight Imperium: Fourth Edition",
                yearPublished = 2017,
                thumbnailUrl = "https://picsum.photos/seed/twilight-imperium/200",
                imageUrl = "https://picsum.photos/seed/twilight-imperium/600",
                minPlayers = 3,
                maxPlayers = 6,
                playingTimeMinutes = 480,
                description = "Epico gioco di strategia, diplomazia e conquista galattica.",
                lastSyncedAt = Instant.now(),
            ),
        )

        return staticSpecs.map { spec ->
            gameRepository.findByBggId(spec.bggId).orElseGet { gameRepository.save(spec) }
        }
    }
}