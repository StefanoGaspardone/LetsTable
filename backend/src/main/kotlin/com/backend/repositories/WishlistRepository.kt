package com.backend.repositories

import com.backend.models.entities.Wishlist
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WishlistRepository: JpaRepository<Wishlist, UUID> {

    fun findAllByOwnerId(ownerId: UUID): List<Wishlist>

    @Query(
        """
        SELECT DISTINCT w FROM Wishlist w
        LEFT JOIN WishlistMember wm ON wm.wishlist.id = w.id
        WHERE w.owner.id = :userId OR wm.user.id = :userId
        """
    )
    fun findAllAccessibleByUser(@Param("userId") userId: UUID): List<Wishlist>
}