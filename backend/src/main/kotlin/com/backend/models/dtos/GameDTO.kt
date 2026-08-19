package com.backend.models.dtos

import com.backend.models.entities.Game
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "A single game result from a BoardGameGeek search")
data class GameSearchResultResponse(
    @field:Schema(description = "BoardGameGeek internal id")
    val bggId: Long,

    @field:Schema(description = "Game name")
    val name: String,

    @field:Schema(description = "Year of first publication")
    val yearPublished: Int?,
)

@Schema(description = "A game currently trending on BoardGameGeek")
data class HotGameResponse(
    @field:Schema(description = "BoardGameGeek internal id")
    val bggId: Long,

    @field:Schema(description = "Current position in the hotness ranking")
    val rank: Int,

    @field:Schema(description = "Game name")
    val name: String,

    @field:Schema(description = "Thumbnail image URL")
    val thumbnailUrl: String?,

    @field:Schema(description = "Year of first publication")
    val yearPublished: Int?,
)

@Schema(description = "Full details of a game, cached locally from BoardGameGeek")
data class GameDTO(
    @field:Schema(description = "Internal Let's Table id")
    val id: UUID,

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
) {
    companion object {
        fun from(game: Game) = GameDTO(
            id = game.id!!,
            bggId = game.bggId,
            name = game.name,
            yearPublished = game.yearPublished,
            thumbnailUrl = game.thumbnailUrl,
            imageUrl = game.imageUrl,
            minPlayers = game.minPlayers,
            maxPlayers = game.maxPlayers,
            playingTimeMinutes = game.playingTimeMinutes,
            description = game.description,
        )
    }
}