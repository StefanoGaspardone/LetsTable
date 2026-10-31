package com.backend.models.dtos

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Summary statistics for the current user")
data class UserStatsDTO(
    @field:Schema(description = "Total completed matches (created or participated)", example = "42")
    val totalMatches: Long,

    @field:Schema(description = "Total matches won", example = "17")
    val totalWins: Long,

    @field:Schema(description = "Total games in the collection, expansions included", example = "35")
    val totalGames: Long,

    @field:Schema(description = "Total accepted friends", example = "8")
    val totalFriends: Long,
)