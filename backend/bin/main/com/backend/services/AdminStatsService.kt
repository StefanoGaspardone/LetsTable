package com.backend.services

import com.backend.models.dtos.AdminStatsDTO
import com.backend.models.dtos.GamePopularityDTO
import com.backend.models.enums.AccountStatus
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistItemRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class AdminStatsService(
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val collectionItemRepository: CollectionItemRepository,
    private val wishlistItemRepository: WishlistItemRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TOP_GAMES_LIMIT = 10
    }

    fun getStats(): AdminStatsDTO {
        logger.debug("\n\t[DEBUG] [admin_stats_service][get_stats] Computing aggregated statistics")

        try {
            val totalUsers = userRepository.count()
            val activeUsers = userRepository.countByAccountStatus(AccountStatus.ACTIVE)
            val inactiveUsers = userRepository.countByAccountStatus(AccountStatus.INACTIVE)

            val activationRate = if(activeUsers + inactiveUsers == 0L) {
                0.0
            } else {
                activeUsers.toDouble() / (activeUsers + inactiveUsers)
            }

            val totalMatches = matchRepository.count()

            val pageable = PageRequest.of(0, TOP_GAMES_LIMIT)
            val mostOwnedGames = collectionItemRepository.findMostOwnedGames(pageable).map { GamePopularityDTO.from(it) }
            val mostPlayedGames = matchRepository.findMostPlayedGames(pageable).map { GamePopularityDTO.from(it) }
            val mostWishedGames = wishlistItemRepository.findMostWishedGames(pageable).map { GamePopularityDTO.from(it) }

            val response = AdminStatsDTO(
                totalUsers = totalUsers,
                activeUsers = activeUsers,
                activationRate = activationRate,
                totalMatches = totalMatches,
                mostOwnedGames = mostOwnedGames,
                mostPlayedGames = mostPlayedGames,
                mostWishedGames = mostWishedGames,
            )

            logger.info("\n\t[INFO] [admin_stats_service][get_stats] Statistics computed successfully")
            return response
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_stats_service][get_stats] Error computing statistics: {}", e.message)
            throw e
        }
    }
}