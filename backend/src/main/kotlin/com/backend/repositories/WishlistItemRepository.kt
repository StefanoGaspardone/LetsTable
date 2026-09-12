package com.backend.repositories

import com.backend.models.entities.WishlistItem
import com.backend.models.projections.GamePopularityProjection
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface WishlistItemRepository: JpaRepository<WishlistItem, UUID>, JpaSpecificationExecutor<WishlistItem> {

    fun existsByWishlistIdAndGameId(wishlistId: UUID, gameId: UUID): Boolean

    fun findByIdAndWishlistId(itemId: UUID, wishlistId: UUID): Optional<WishlistItem>

    fun findByWishlistIdAndGameId(wishlistId: UUID, gameId: UUID): Optional<WishlistItem>

    @Query(
        """
        SELECT wi.game.id AS gameId, wi.game.name AS gameName, COUNT(wi) AS count
        FROM WishlistItem wi
        GROUP BY wi.game.id, wi.game.name
        ORDER BY COUNT(wi) DESC
        """
    )
    fun findMostWishedGames(pageable: Pageable): List<GamePopularityProjection>
}