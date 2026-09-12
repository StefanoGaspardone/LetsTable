package com.backend.models.dtos

import com.backend.models.entities.Game
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Administrative representation of a game, including sync metadata")
data class AdminGameDTO(
    @field:Schema(description = "Internal Let's Table id")
    val id: UUID,

    @field:Schema(description = "BoardGameGeek internal id")
    val bggId: Long,

    @field:Schema(description = "Game name")
    val name: String,

    @field:Schema(description = "Current position in the BGG hotness ranking, null if not currently trending")
    val rank: Int?,

    @field:Schema(description = "Date and time this game's core data was last synced from BoardGameGeek")
    val lastSyncedAt: Instant,

    @field:Schema(description = "Date and time this game's sleeve/component data was last synced, null if never synced")
    val sleevesSyncedAt: Instant?,
) {
    companion object {
        fun from(game: Game) = AdminGameDTO(
            id = game.id!!,
            bggId = game.bggId,
            name = game.name,
            rank = game.rank,
            lastSyncedAt = game.lastSyncedAt,
            sleevesSyncedAt = game.sleevesSyncedAt,
        )
    }
}