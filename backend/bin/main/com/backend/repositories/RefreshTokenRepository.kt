package com.backend.repositories

import com.backend.models.entities.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface RefreshTokenRepository: JpaRepository<RefreshToken, UUID> {

    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>
}