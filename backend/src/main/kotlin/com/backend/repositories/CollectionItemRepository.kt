package com.backend.repositories

import com.backend.models.entities.CollectionItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CollectionItemRepository : JpaRepository<CollectionItem, UUID> {

    fun existsByUserIdAndGameId(userId: UUID, gameId: UUID): Boolean

    fun findAllByUserId(userId: UUID): List<CollectionItem>
}