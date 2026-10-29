package com.backend.repositories

import com.backend.models.entities.WishlistMember
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface WishlistMemberRepository: JpaRepository<WishlistMember, UUID> {

    fun existsByWishlistIdAndUserId(wishlistId: UUID, userId: UUID): Boolean

    fun findByWishlistIdAndUserId(wishlistId: UUID, userId: UUID): Optional<WishlistMember>

    @EntityGraph(attributePaths = ["user"])
    fun findAllByWishlistId(wishlistId: UUID): List<WishlistMember>

    @Modifying
    @Query("DELETE FROM WishlistMember wm WHERE wm.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID)

    @Modifying
    @Query("DELETE FROM WishlistMember wm WHERE wm.wishlist.id IN (SELECT w.id FROM Wishlist w WHERE w.owner.id = :ownerId)")
    fun deleteAllByWishlistOwnerId(@Param("ownerId") ownerId: UUID)
}