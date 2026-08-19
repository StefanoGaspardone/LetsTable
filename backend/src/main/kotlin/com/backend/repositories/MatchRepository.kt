package com.backend.repositories

import com.backend.models.entities.Match
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MatchRepository: JpaRepository<Match, UUID> {

    @Query(
        """
        SELECT DISTINCT m FROM Match m
        LEFT JOIN MatchPlayer mp ON mp.match = m
        WHERE m.createdBy.id = :userId OR mp.user.id = :userId
        ORDER BY m.playedAt DESC
        """
    )
    fun findAllForUser(@Param("userId") userId: UUID): List<Match>
}