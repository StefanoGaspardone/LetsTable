package com.backend.repositories

import com.backend.models.entities.PushToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface PushTokenRepository: JpaRepository<PushToken, UUID> {

    fun findByToken(token: String): Optional<PushToken>

    fun findAllByUserId(userId: UUID): List<PushToken>

    fun deleteByToken(token: String)
}