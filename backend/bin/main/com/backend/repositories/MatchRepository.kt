package com.backend.repositories

import com.backend.models.entities.Match
import com.backend.models.projections.GamePopularityProjection
import com.backend.models.projections.MatchDayCountProjection
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Repository
interface MatchRepository: JpaRepository<Match, UUID>, JpaSpecificationExecutor<Match> {

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
        SELECT CAST(m.played_at AT TIME ZONE 'UTC' AS date) AS "playedAt", COUNT(DISTINCT m.id) AS "matchCount"
        FROM matches m
        LEFT JOIN match_players mp ON mp.match_id = m.id
        WHERE (m.created_by_user_id = :userId OR mp.user_id = :userId)
        AND m.played_at >= :from
        AND m.played_at < :to
        GROUP BY CAST(m.played_at AT TIME ZONE 'UTC' AS date)
        ORDER BY CAST(m.played_at AT TIME ZONE 'UTC' AS date) ASC
        """,
        nativeQuery = true,
    )
    fun countMatchesByDay(@Param("userId") userId: UUID, @Param("from") from: Instant, @Param("to") to: Instant): List<MatchDayCountProjection>

    @Query(
        """
        SELECT DISTINCT m FROM Match m
        JOIN FETCH m.game
        LEFT JOIN MatchPlayer mp ON mp.match = m
        WHERE m.createdBy.id = :userId OR mp.user.id = :userId
        ORDER BY m.playedAt DESC, m.createdAt DESC
        """
    )
    fun findRecentForUser(@Param("userId") userId: UUID, pageable: Pageable): List<Match>

    @Query(
        """
        SELECT COUNT(DISTINCT m.id) FROM Match m
        LEFT JOIN MatchPlayer mp ON mp.match = m
        WHERE (m.createdBy.id = :userId OR mp.user.id = :userId)
        AND m.durationMinutes IS NOT NULL
        """
    )
    fun countCompletedMatchesForUser(@Param("userId") userId: UUID): Long

    @Query(
        """
        SELECT COUNT(DISTINCT m.id) FROM Match m
        LEFT JOIN MatchPlayer mp ON mp.match = m AND mp.user.id = :userId
        LEFT JOIN MatchTeam mt ON mp.team = mt
        WHERE m.durationMinutes IS NOT NULL
        AND (
            mp.isWinner = true
            OR mt.isWinner = true
        )
        """
    )
    fun countWonMatchesForUser(@Param("userId") userId: UUID): Long

    @Query(
        """
        SELECT m.game.id AS gameId, m.game.name AS gameName, COUNT(DISTINCT m) AS count
        FROM Match m
        GROUP BY m.game.id, m.game.name
        ORDER BY COUNT(DISTINCT m) DESC
        """
    )
    fun findMostPlayedGames(pageable: Pageable): List<GamePopularityProjection>
}