package com.backend.repositories

import com.backend.models.entities.UserAchievement
import com.backend.models.enums.FriendRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface UserAchievementRepository: JpaRepository<UserAchievement, UUID> {

    fun findAllByUserId(userId: UUID): List<UserAchievement>

    fun findAllByUserIdAndSeenAtIsNullOrderByUnlockedAtAsc(userId: UUID): List<UserAchievement>

    @Query("SELECT ua.achievementCode FROM UserAchievement ua WHERE ua.userId = :userId")
    fun findCodesByUserId(@Param("userId") userId: UUID): Set<String>

    @Modifying
    @Query(
        value = """
            INSERT INTO user_achievements(id, user_id, achievement_code, unlocked_at)
            VALUES(gen_random_uuid(), :userId, :code, :unlockedAt)
            ON CONFLICT(user_id, achievement_code) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(@Param("userId") userId: UUID, @Param("code") code: String, @Param("unlockedAt") unlockedAt: Instant): Int

    @Modifying
    @Query(
        """
        UPDATE UserAchievement ua SET ua.seenAt = :now
        WHERE ua.userId = :userId AND ua.seenAt IS NULL AND ua.achievementCode IN :codes
        """
    )
    fun markSeen(@Param("userId") userId: UUID, @Param("codes") codes: Collection<String>, @Param("now") now: Instant): Int

    @Query(
        """
        SELECT m.playedAt FROM MatchPlayer mp JOIN mp.match m
        WHERE mp.user.id = :userId AND m.durationMinutes IS NOT NULL
        ORDER BY m.playedAt ASC, m.createdAt ASC
        """
    )
    fun findFinishedMatchDates(@Param("userId") userId: UUID): List<Instant>

    @Query(
        """
        SELECT CASE WHEN (t.isWinner = true OR (t.id IS NULL AND mp.isWinner = true))
                    THEN true ELSE false END
        FROM MatchPlayer mp JOIN mp.match m LEFT JOIN mp.team t
        WHERE mp.user.id = :userId AND m.durationMinutes IS NOT NULL
        ORDER BY m.playedAt ASC, m.createdAt ASC
        """
    )
    fun findFinishedMatchWins(@Param("userId") userId: UUID): List<Boolean>

    @Query(
        """
        SELECT COUNT(DISTINCT m.game.id) FROM MatchPlayer mp JOIN mp.match m
        WHERE mp.user.id = :userId AND m.durationMinutes IS NOT NULL
        """
    )
    fun countDistinctGamesPlayed(@Param("userId") userId: UUID): Long

    @Query("SELECT COUNT(ci) FROM CollectionItem ci WHERE ci.user.id = :userId")
    fun countCollectionItems(@Param("userId") userId: UUID): Long

    @Query(
        """
        SELECT COUNT(fr) FROM FriendRequest fr
        WHERE fr.status = :status AND (fr.sender.id = :userId OR fr.receiver.id = :userId)
        """
    )
    fun countFriends(@Param("userId") userId: UUID, @Param("status") status: FriendRequestStatus): Long
}