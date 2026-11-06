package com.backend.repositories

import com.backend.models.entities.EmailVerification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface EmailVerificationRepository: JpaRepository<EmailVerification, UUID> {

    fun findByUserId(userId: UUID): Optional<EmailVerification>

    @Modifying
    @Query("DELETE FROM EmailVerification ev WHERE ev.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID): Int
}