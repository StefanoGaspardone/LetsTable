package com.backend.repositories

import com.backend.models.entities.MatchTeam
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MatchTeamRepository: JpaRepository<MatchTeam, UUID> {

    fun findAllByMatchId(matchId: UUID): List<MatchTeam>

    fun deleteAllByMatchId(matchId: UUID)
}