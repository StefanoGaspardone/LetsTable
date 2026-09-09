package com.backend.repositories

import com.backend.models.entities.CollectionItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CollectionItemRepository : JpaRepository<CollectionItem, UUID>, JpaSpecificationExecutor<CollectionItem> {

    fun existsByUserIdAndGameId(userId: UUID, gameId: UUID): Boolean

    fun findByUserIdAndGameId(userId: UUID, gameId: UUID): Optional<CollectionItem>

    @Query("SELECT ci.game.id FROM CollectionItem ci WHERE ci.user.id = :userId AND ci.game.id IN :gameIds")
    fun findGameIdsInCollection(userId: UUID, gameIds: List<UUID>): Set<UUID>
}