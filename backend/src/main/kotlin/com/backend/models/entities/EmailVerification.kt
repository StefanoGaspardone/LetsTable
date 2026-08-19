package com.backend.models.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "email_verifications")
class EmailVerification(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User,

    @Column(name = "otp_hash", nullable = false)
    var otpHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_sent_at", nullable = false)
    var lastSentAt: Instant,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {

    fun isExpired(): Boolean = expiresAt.isBefore(Instant.now())

    fun hasExceededMaxAttempts(maxAttempts: Int): Boolean = attempts >= maxAttempts
}