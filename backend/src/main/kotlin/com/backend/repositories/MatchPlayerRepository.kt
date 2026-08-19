package com.backend.repositories

import com.backend.models.entities.MatchPlayer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MatchPlayerRepository: JpaRepository<MatchPlayer, UUID> {

    fun findAllByMatchId(matchId: UUID): List<MatchPlayer>

    fun deleteAllByMatchId(matchId: UUID)
}