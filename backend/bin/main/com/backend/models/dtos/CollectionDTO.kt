package com.backend.models.dtos

import com.backend.models.entities.CollectionItem
import io.swagger.v3.oas.annotations.media.Schema
import org.jetbrains.annotations.NotNull
import java.time.Instant
import java.util.UUID

@Schema(description = "Payload to add a game to the current user's collection")
data class AddToCollectionRequest(
    @field:Schema(description = "Internal id of the game (obtained via GET /games/{bggId})")
    @field:NotNull
    val gameId: UUID,
)

@Schema(description = "A game in the current user's collection")
data class CollectionItemDTO(
    @field:Schema(description = "Collection entry id")
    val id: UUID,

    @field:Schema(description = "Game details")
    val game: GameDTO,

    @field:Schema(description = "When the game was added to the collection")
    val addedAt: Instant,
) {
    companion object {
        fun from(item: CollectionItem) = CollectionItemDTO(
            id = item.id!!,
            game = GameDTO.from(item.game),
            addedAt = item.createdAt,
        )
    }
}

@Schema(description = "Whether a game is in the current user's collection")
data class CollectionStatusDTO(
    @field:Schema(description = "Whether the game is in the collection")
    val inCollection: Boolean,

    @field:Schema(description = "Id of the collection entry, if present")
    val itemId: UUID?,
)