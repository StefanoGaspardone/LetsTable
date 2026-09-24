package com.backend.repositories

import com.backend.models.entities.Wishlist
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WishlistRepository: JpaRepository<Wishlist, UUID>, JpaSpecificationExecutor<Wishlist>