package com.backend.repositories

import com.backend.models.entities.WishlistItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface WishlistItemRepository: JpaRepository<WishlistItem, UUID> {

    fun existsByWishlistIdAndGameId(wishlistId: UUID, gameId: UUID): Boolean

    fun findAllByWishlistId(wishlistId: UUID): List<WishlistItem>

    fun findByIdAndWishlistId(itemId: UUID, wishlistId: UUID): Optional<WishlistItem>
}