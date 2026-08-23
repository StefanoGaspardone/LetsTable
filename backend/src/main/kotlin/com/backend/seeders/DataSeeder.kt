package com.backend.seeders

import com.backend.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class DataSeeder(
    private val userRepository: UserRepository,
    private val userSeeder: UserSeeder,
    private val gameSeeder: GameSeeder,
    private val collectionSeeder: CollectionSeeder,
    private val wishlistSeeder: WishlistSeeder,
    private val matchSeeder: MatchSeeder,
    @Value($$"${seeding.enabled}") private val seedingEnabled: Boolean,
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String) {
        if(!seedingEnabled) {
            logger.debug("\n\t[DEBUG] [demo_data_seeder][run] Seeding disabled, skipping")
            return
        }

        logger.debug("\n\t[DEBUG] [demo_data_seeder][run] Starting demo data seeding")

        try {
            val alreadySeeded = userRepository.existsByUsernameIgnoreCase("marco")

            val users = userSeeder.seed()
            val games = gameSeeder.seed()

            if(alreadySeeded) {
                logger.info("\n\t[INFO] [demo_data_seeder][run] Demo users already existed, skipping collection/wishlist/match seeding to avoid duplicates")
                return
            }

            collectionSeeder.seed(users, games)
            wishlistSeeder.seed(users, games)
            matchSeeder.seed(users, games)

            logger.info("\n\t[INFO] [demo_data_seeder][run] Demo data seeding completed")
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [demo_data_seeder][run] Error during demo data seeding: {}", e.message)
            throw e
        }
    }
}