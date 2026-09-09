package com.backend.repositories

import com.backend.models.entities.WishlistMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface WishlistMemberRepository: JpaRepository<WishlistMember, UUID> {

    fun existsByWishlistIdAndUserId(wishlistId: UUID, userId: UUID): Boolean

    fun findByWishlistIdAndUserId(wishlistId: UUID, userId: UUID): Optional<WishlistMember>

    fun findAllByWishlistId(wishlistId: UUID): List<WishlistMember>
}