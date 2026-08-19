package com.backend.repositories

import com.backend.models.entities.Game
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface GameRepository: JpaRepository<Game, UUID> {

    fun findByBggId(bggId: Long): Optional<Game>
}