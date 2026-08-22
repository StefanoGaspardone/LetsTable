package com.backend.repositories

import com.backend.models.entities.CollectionItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CollectionItemRepository : JpaRepository<CollectionItem, UUID>, JpaSpecificationExecutor<CollectionItem> {

    fun existsByUserIdAndGameId(userId: UUID, gameId: UUID): Boolean

    fun findByUserIdAndGameId(userId: UUID, gameId: UUID): Optional<CollectionItem>
}