package com.backend.models.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "user_achievements",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_user_achievements_user_code",
            columnNames = ["user_id", "achievement_code"]
        )
    ]
)
class UserAchievement(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "achievement_code", nullable = false, length = 64)
    var achievementCode: String,

    @Column(name = "unlocked_at", nullable = false)
    var unlockedAt: Instant,

    @Column(name = "seen_at", nullable = true)
    var seenAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)