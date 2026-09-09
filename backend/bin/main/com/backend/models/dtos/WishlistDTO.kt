package com.backend.models.dtos

import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import com.backend.models.entities.WishlistMember
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Schema(description = "Payload to create a new wishlist")
data class CreateWishlistRequest(
    @field:Schema(description = "Wishlist name", example = "Games I want")
    @field:NotBlank
    @field:Size(min = 1, max = 100)
    val name: String,

    @field:Schema(description = "Whether other users can be added as members to add/remove games", example = "false")
    @field:NotNull
    val isShared: Boolean,
)

@Schema(description = "Payload to add a member to a shared wishlist")
data class AddWishlistMemberRequest(
    @field:Schema(description = "Id of the user to add as a member")
    @field:NotNull
    val userId: UUID,
)

@Schema(description = "Payload to add a game to a wishlist")
data class AddWishlistItemRequest(
    @field:Schema(description = "Internal id of the game (obtained via GET /games/{bggId})")
    @field:NotNull
    val gameId: UUID,
)

@Schema(description = "A wishlist, personal or shared")
data class WishlistDTO(
    @field:Schema(description = "Wishlist id")
    val id: UUID,

    @field:Schema(description = "Wishlist name")
    val name: String,

    @field:Schema(description = "Public profile of the wishlist owner")
    val owner: UserDTO,

    @field:Schema(description = "Whether the wishlist is shared with other members")
    val isShared: Boolean,

    @field:Schema(description = "Whether this is the user's fixed default wishlist (cannot be deleted or shared)")
    val isDefault: Boolean,

    @field:Schema(description = "When the wishlist was created")
    val createdAt: Instant,
) {
    companion object {
        fun from(wishlist: Wishlist) = WishlistDTO(
            id = wishlist.id!!,
            name = wishlist.name,
            owner = UserDTO.from(wishlist.owner),
            isShared = wishlist.isShared,
            isDefault = wishlist.isDefault,
            createdAt = wishlist.createdAt,
        )
    }
}

@Schema(description = "A member of a shared wishlist")
data class WishlistMemberDTO(
    @field:Schema(description = "Membership entry id")
    val id: UUID,

    @field:Schema(description = "Public profile of the member")
    val user: UserDTO,

    @field:Schema(description = "When the member was added")
    val addedAt: Instant,
) {
    companion object {
        fun from(member: WishlistMember) = WishlistMemberDTO(
            id = member.id!!,
            user = UserDTO.from(member.user),
            addedAt = member.createdAt,
        )
    }
}

@Schema(description = "A game in a wishlist")
data class WishlistItemDTO(
    @field:Schema(description = "Wishlist item id")
    val id: UUID,

    @field:Schema(description = "Game details")
    val game: GameDTO,

    @field:Schema(description = "Public profile of the user who added this game")
    val addedBy: UserDTO,

    @field:Schema(description = "When the game was added")
    val addedAt: Instant,
) {
    companion object {
        fun from(item: WishlistItem) = WishlistItemDTO(
            id = item.id!!,
            game = GameDTO.from(item.game),
            addedBy = UserDTO.from(item.addedBy),
            addedAt = item.createdAt,
        )
    }
}

@Schema(description = "Whether a game is in a specific wishlist")
data class WishlistItemStatusDTO(
    @field:Schema(description = "Whether the game is in the wishlist")
    val inWishlist: Boolean,

    @field:Schema(description = "Id of the wishlist item, if present")
    val itemId: UUID?,
)