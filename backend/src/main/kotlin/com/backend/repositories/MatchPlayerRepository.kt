package com.backend.repositories

import com.backend.models.entities.MatchPlayer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MatchPlayerRepository: JpaRepository<MatchPlayer, UUID> {

    fun findAllByMatchId(matchId: UUID): List<MatchPlayer>

    @Query(
        """
        SELECT mp FROM MatchPlayer mp
        LEFT JOIN FETCH mp.user
        LEFT JOIN FETCH mp.team
        WHERE mp.match.id IN :matchIds
        """
    )
    fun findAllByMatchIdIn(@Param("matchIds") matchIds: Collection<UUID>): List<MatchPlayer>

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MatchPlayer mp WHERE mp.match.id = :matchId")
    fun deleteAllByMatchId(@Param("matchId") matchId: UUID)
}