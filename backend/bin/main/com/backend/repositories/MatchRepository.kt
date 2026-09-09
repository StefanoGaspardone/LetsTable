package com.backend.repositories

import com.backend.models.entities.Match
import com.backend.models.projections.MatchDayCountProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.*

interface MatchRepository : JpaRepository<Match, UUID>, JpaSpecificationExecutor<Match> {

    @Query(
        """
        SELECT DISTINCT m FROM Match m
        LEFT JOIN MatchPlayer mp ON mp.match = m
        WHERE m.createdBy.id = :userId OR mp.user.id = :userId
        """
    )
    fun findAllForUser(@Param("userId") userId: UUID): List<Match>

    @Query(
        """
        SELECT m.playedAt as playedAt, COUNT(DISTINCT m.id) as matchCount
        FROM Match m
        LEFT JOIN MatchPlayer mp ON mp.match = m
        WHERE (m.createdBy.id = :userId OR mp.user.id = :userId)
        AND m.playedAt BETWEEN :from AND :to
        GROUP BY m.playedAt
        ORDER BY m.playedAt ASC
        """
    )
    fun countMatchesByDay(
        @Param("userId") userId: UUID,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<MatchDayCountProjection>
}