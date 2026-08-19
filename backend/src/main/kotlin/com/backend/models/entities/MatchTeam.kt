package com.backend.models.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "match_teams")
class MatchTeam(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    var match: Match,

    @Column(name = "name", nullable = true, length = 50)
    var name: String? = null,

    @Column(name = "color", nullable = false, length = 20)
    var color: String,

    @Column(name = "score", nullable = false)
    var score: Int = 0,

    @Column(name = "is_winner", nullable = false)
    var isWinner: Boolean = false,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)