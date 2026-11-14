package com.backend.repositories

import com.backend.models.entities.GameImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface GameImageRepository : JpaRepository<GameImage, UUID> {

    fun findAllByGameIdOrderByPositionAsc(gameId: UUID): List<GameImage>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GameImage i WHERE i.game.id = :gameId")
    fun deleteAllByGameId(@Param("gameId") gameId: UUID)
}