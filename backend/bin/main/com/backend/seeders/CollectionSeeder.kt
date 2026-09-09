package com.backend.seeders

import com.backend.models.entities.CollectionItem
import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.repositories.CollectionItemRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CollectionSeeder(
    private val collectionItemRepository: CollectionItemRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun seed(users: List<User>, games: List<Game>) {
        logger.debug("\n\t[DEBUG] [collection_seeder][seed] Seeding demo collection items")

        try {
            val (marco, anna, luca, _) = users
            val (arkNova, seti, dune, twilight) = games

            val assignments = listOf(
                marco to arkNova,
                marco to seti,
                anna to dune,
                anna to twilight,
                luca to arkNova,
            )

            var insertedCount = 0
            assignments.forEach { (user, game) ->
                if(!collectionItemRepository.existsByUserIdAndGameId(user.id!!, game.id!!)) {
                    collectionItemRepository.save(CollectionItem(user = user, game = game))
                    insertedCount++
                }
            }

            logger.info("\n\t[INFO] [collection_seeder][seed] {} collection items seeded", insertedCount)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [collection_seeder][seed] Error seeding collection: {}", e.message)
            throw e
        }
    }
}