package com.backend.models.dtos

import com.backend.models.entities.Game
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(description = "Full or partial details of a game, sourced from BoardGameGeek")
data class GameDTO(
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

    @field:Schema(description = "Whether this game is in the current user's collection, null if not applicable/not checked")
    val inCollection: Boolean? = null,

    @field:Schema(description = "Base game if this game is an expansion, null if not applicable/not checked")
    val baseGame: GameDTO? = null,

    @field:Schema(description = "Average complexity/weight rating from 1 to 5, null if not yet fully synced")
    val difficulty: Double?,

    @field:Schema(description = "List of game designers")
    val designers: List<String>,

    @field:Schema(description = "List of game artists")
    val artists: List<String>,

    @field:Schema(description = "List of publishers")
    val publishers: List<String>,
) {
    companion object {
        fun from(game: Game, inCollection: Boolean? = null, baseGame: GameDTO? = null) = GameDTO(
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
            expansions = if (game.isExpansion == null) null else game.expansionRefs.size.toLong(),
            isExpansion = game.isExpansion,
            rank = game.rank,
            inCollection = inCollection,
            baseGame = baseGame,
            difficulty = game.difficulty,
            designers = game.designers,
            artists = game.artists,
            publishers = game.publishers,
        )

        fun fromSearchResult(item: BggSearchItemXml) = GameDTO(
            id = null,
            bggId = item.id,
            name = item.name?.value ?: "Sconosciuto",
            yearPublished = item.yearPublished?.value?.toIntOrNull(),
            thumbnailUrl = null,
            imageUrl = null,
            minPlayers = null,
            maxPlayers = null,
            playingTimeMinutes = null,
            description = null,
            bestWith = null,
            recommendedWith = null,
            expansions = null,
            isExpansion = null,
            rank = null,
            inCollection = null,
            baseGame = null,
            difficulty = null,
            designers = emptyList(),
            artists = emptyList(),
            publishers = emptyList(),
        )
    }
}