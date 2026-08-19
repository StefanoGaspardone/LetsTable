package com.backend.repositories

import com.backend.models.entities.PasswordReset
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface PasswordResetRepository: JpaRepository<PasswordReset, UUID> {

    fun findByUserId(userId: UUID): Optional<PasswordReset>
}