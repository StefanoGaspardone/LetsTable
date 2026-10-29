package com.backend.repositories

import com.backend.models.entities.MatchTeam
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MatchTeamRepository: JpaRepository<MatchTeam, UUID> {

    fun findAllByMatchId(matchId: UUID): List<MatchTeam>

    fun findAllByMatchIdIn(matchIds: Collection<UUID>): List<MatchTeam>

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MatchTeam mt WHERE mt.match.id = :matchId")
    fun deleteAllByMatchId(@Param("matchId") matchId: UUID)
}