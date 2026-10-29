package com.backend.repositories

import com.backend.models.entities.PushToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface PushTokenRepository: JpaRepository<PushToken, UUID> {

    fun findByToken(token: String): Optional<PushToken>

    fun findAllByUserId(userId: UUID): List<PushToken>

    fun deleteByToken(token: String)

    @Modifying
    @Query("DELETE FROM PushToken pt WHERE pt.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID)
}