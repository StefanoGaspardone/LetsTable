package com.backend.repositories

import com.backend.models.entities.Wishlist
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface WishlistRepository: JpaRepository<Wishlist, UUID>, JpaSpecificationExecutor<Wishlist> {

    @Query("SELECT w FROM Wishlist w WHERE w.owner.id = :ownerId AND w.isDefault = true")
    fun findDefaultByOwnerId(@Param("ownerId") ownerId: UUID): Optional<Wishlist>
}