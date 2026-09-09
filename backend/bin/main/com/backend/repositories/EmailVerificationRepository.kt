package com.backend.repositories

import com.backend.models.entities.EmailVerification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface EmailVerificationRepository: JpaRepository<EmailVerification, UUID> {

    fun findByUserId(userId: UUID): Optional<EmailVerification>
}