package com.backend.repositories

import com.backend.models.entities.GameSleeve
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GameSleeveRepository : JpaRepository<GameSleeve, UUID> {

    fun findAllByGameId(gameId: UUID): List<GameSleeve>

    @Modifying
    @Query("DELETE FROM GameSleeve gs WHERE gs.game.id = :gameId")
    fun deleteAllByGameId(gameId: UUID)

    fun findAllByGameIdIn(gameIds: List<UUID>): List<GameSleeve>
}