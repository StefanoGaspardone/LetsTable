package com.backend.repositories

import com.backend.models.entities.HotGame
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface HotGameRepository: JpaRepository<HotGame, UUID> {
    fun findAllByOrderByRankAsc(): List<HotGame>
}