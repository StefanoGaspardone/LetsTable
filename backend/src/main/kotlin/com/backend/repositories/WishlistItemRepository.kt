package com.backend.repositories

import com.backend.models.entities.WishlistItem
import com.backend.models.projections.GamePopularityProjection
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface WishlistItemRepository: JpaRepository<WishlistItem, UUID>, JpaSpecificationExecutor<WishlistItem> {

    fun existsByWishlistIdAndGameId(wishlistId: UUID, gameId: UUID): Boolean

    fun findByIdAndWishlistId(itemId: UUID, wishlistId: UUID): Optional<WishlistItem>

    @Query("SELECT wi.id FROM WishlistItem wi WHERE wi.wishlist.id = :wishlistId AND wi.game.id = :gameId")
    fun findIdByWishlistIdAndGameId(@Param("wishlistId") wishlistId: UUID, @Param("gameId") gameId: UUID): UUID?

    @Query(
        """
        SELECT wi.game.id AS gameId, wi.game.name AS gameName, wi.game.thumbnailUrl AS gameThumbnailUrl, COUNT(wi) AS count
        FROM WishlistItem wi
        GROUP BY wi.game.id, wi.game.name, wi.game.thumbnailUrl
        ORDER BY COUNT(wi) DESC
        """
    )
    fun findMostWishedGames(pageable: Pageable): List<GamePopularityProjection>

    @Modifying
    @Query("DELETE FROM WishlistItem wi WHERE wi.wishlist.id IN (SELECT w.id FROM Wishlist w WHERE w.owner.id = :ownerId)")
    fun deleteAllByWishlistOwnerId(@Param("ownerId") ownerId: UUID)
}