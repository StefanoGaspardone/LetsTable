package com.backend.repositories

import com.backend.models.entities.Game
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface GameRepository: JpaRepository<Game, UUID> {

    fun findByBggId(bggId: Long): Optional<Game>

    fun findAllByRankIsNotNullOrderByRankAsc(pageable: Pageable): Page<Game>

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Game g SET g.rank = null")
    fun clearAllRanks()
}