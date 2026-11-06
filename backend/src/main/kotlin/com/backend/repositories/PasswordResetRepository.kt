package com.backend.repositories

import com.backend.models.entities.PasswordReset
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface PasswordResetRepository: JpaRepository<PasswordReset, UUID> {

    fun findByUserId(userId: UUID): Optional<PasswordReset>

    @Modifying
    @Query("DELETE FROM PasswordReset pr WHERE pr.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID): Int
}