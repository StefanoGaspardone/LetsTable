package com.backend.models.dtos

import com.backend.models.entities.Game
import com.backend.models.entities.GameSleeve
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Administrative representation of a game, including all public game details plus sync metadata")
data class AdminGameDTO(
    @field:Schema(description = "Internal Let's Table id")
    val id: UUID?,

    @field:Schema(description = "BoardGameGeek internal id")
    val bggId: Long,

    @field:Schema(description = "Game name")
    val name: String,

    @field:Schema(description = "Year of first publication")
    val yearPublished: Int?,

    @field:Schema(description = "Thumbnail image URL")
    val thumbnailUrl: String?,

    @field:Schema(description = "Full-size image URL")
    val imageUrl: String?,

    @field:Schema(description = "Minimum number of players")
    val minPlayers: Int?,

    @field:Schema(description = "Maximum number of players")
    val maxPlayers: Int?,

    @field:Schema(description = "Average playing time in minutes")
    val playingTimeMinutes: Int?,

    @field:Schema(description = "Game description")
    val description: String?,

    @field:Schema(description = "Best number of players")
    val bestWith: String?,

    @field:Schema(description = "Recommended number of players")
    val recommendedWith: String?,

    @field:Schema(description = "Number of expansions available, null if not yet fully synced")
    val expansions: Long?,

    @field:Schema(description = "Whether this game is itself an expansion, null if not yet fully synced")
    val isExpansion: Boolean?,

    @field:Schema(description = "Current position in the BGG hotness ranking, null if not currently trending")
    val rank: Int?,

    @field:Schema(description = "BoardGameGeek's overall ranking position, null if not ranked or not yet synced")
    val bggRank: Int?,

    @field:Schema(description = "Average user rating from 1 to 10, null if not yet fully synced")
    val avgRating: Double?,

    @field:Schema(description = "Average complexity/weight rating from 1 to 5, null if not yet fully synced")
    val difficulty: Double?,

    @field:Schema(description = "List of game designers")
    val designers: List<String>,

    @field:Schema(description = "List of game artists")
    val artists: List<String>,

    @field:Schema(description = "List of publishers")
    val publishers: List<String>,

    @field:Schema(description = "Sleeve/component recommendations, empty if not yet synced or unavailable")
    val sleeves: List<GameSleeveDTO>,

    @field:Schema(description = "Date and time this game's core data was last synced from BoardGameGeek")
    val lastSyncedAt: Instant,

    @field:Schema(description = "Date and time this game's sleeve/component data was last synced, null if never synced")
    val sleevesSyncedAt: Instant?,
) {
    companion object {
        fun from(game: Game, sleeves: List<GameSleeve> = emptyList()) = AdminGameDTO(
            id = game.id,
            bggId = game.bggId,
            name = game.name,
            yearPublished = game.yearPublished,
            thumbnailUrl = game.thumbnailUrl,
            imageUrl = game.imageUrl,
            minPlayers = game.minPlayers,
            maxPlayers = game.maxPlayers,
            playingTimeMinutes = game.playingTimeMinutes,
            description = game.description,
            bestWith = game.bestWith,
            recommendedWith = game.recommendedWith,
            expansions = if(game.isExpansion == null) null else game.expansionRefs.size.toLong(),
            isExpansion = game.isExpansion,
            rank = game.rank,
            bggRank = game.bggRank,
            avgRating = game.avgRating,
            difficulty = game.difficulty,
            designers = game.designers,
            artists = game.artists,
            publishers = game.publishers,
            sleeves = sleeves.map { GameSleeveDTO.from(it) },
            lastSyncedAt = game.lastSyncedAt,
            sleevesSyncedAt = game.sleevesSyncedAt,
        )
    }
}