package com.backend.repositories

import com.backend.models.entities.RefreshToken
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface RefreshTokenRepository: JpaRepository<RefreshToken, UUID> {

    @EntityGraph(attributePaths = ["user"])
    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND t.revoked = false")
    fun revokeAllByUserId(@Param("userId") userId: UUID): Int

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.revoked = true OR t.expiresAt < :now")
    fun deleteRevokedOrExpired(@Param("now") now: Instant): Int
}