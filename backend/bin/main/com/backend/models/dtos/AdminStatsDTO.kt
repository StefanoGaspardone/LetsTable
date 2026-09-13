package com.backend.models.dtos

import com.backend.models.projections.GamePopularityProjection
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "Aggregated statistics about the application's usage")
data class AdminStatsDTO(
    @field:Schema(description = "Total number of registered users, regardless of account status")
    val totalUsers: Long,

    @field:Schema(description = "Number of users with an active account")
    val activeUsers: Long,

    @field:Schema(description = "Ratio of active accounts to (active + inactive) accounts, from 0.0 to 1.0")
    val activationRate: Double,

    @field:Schema(description = "Total number of matches recorded")
    val totalMatches: Long,

    @field:Schema(description = "The 10 most owned games, ranked by number of collections they appear in")
    val mostOwnedGames: List<GamePopularityDTO>,

    @field:Schema(description = "The 10 most played games, ranked by number of matches recorded")
    val mostPlayedGames: List<GamePopularityDTO>,

    @field:Schema(description = "The 10 most wished-for games, ranked by number of wishlists they appear in")
    val mostWishedGames: List<GamePopularityDTO>,
)

@Schema(description = "A game's popularity ranking for a given metric")
data class GamePopularityDTO(
    @field:Schema(description = "Internal Let's Table id of the game")
    val gameId: UUID,

    @field:Schema(description = "Game name")
    val gameName: String,

    @field:Schema(description = "Number of occurrences for this ranking's metric")
    val count: Long,
) {
    companion object {
        fun from(projection: GamePopularityProjection) = GamePopularityDTO(
            gameId = projection.gameId,
            gameName = projection.gameName,
            count = projection.count,
        )
    }
}