package com.backend.seeders

import com.backend.models.entities.Game
import com.backend.repositories.GameRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class GameSeeder(
    private val gameRepository: GameRepository,
    @Value($$"${seeding.enabled}") private val seedingEnabled: Boolean,
): CommandLineRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String) {
        if(!seedingEnabled) {
            logger.debug("\n\t[DEBUG] [game_seeder][run] Seeder disabled, skipping")
            return
        }

        logger.debug("\n\t[DEBUG] [game_seeder][run] Seeding test games")
        try {
            val games = listOf(
                Game(
                    bggId = 342942,
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
                    bggId = 397598,
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
                    bggId = 421006,
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
                    bggId = 233078,
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

            var insertedCount = 0
            games.forEach { game ->
                if(gameRepository.findByBggId(game.bggId).isEmpty) {
                    gameRepository.save(game)
                    insertedCount++
                }
            }

            logger.info("\n\t[INFO] [game_seeder][run] Seeded {} new games ({} already present)", insertedCount, games.size - insertedCount)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_seeder][run] Error seeding games: {}", e.message)
            throw e
        }
    }
}