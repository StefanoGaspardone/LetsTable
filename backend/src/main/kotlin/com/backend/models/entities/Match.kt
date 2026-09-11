package com.backend.models.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "matches")
class Match(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    var game: Game,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: User,

    @Column(name = "is_team_based", nullable = false)
    var isTeamBased: Boolean = false,

    @Column(name = "played_at", nullable = false)
    var playedAt: Instant,

    @Column(name = "duration_minutes", nullable = true)
    var durationMinutes: Int? = null,

    @Column(name = "place", nullable = true)
    var place: String? = null,

    @Column(name = "notes", nullable = true, columnDefinition = "TEXT")
    var notes: String? = null,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "match_expansions",
        joinColumns = [JoinColumn(name = "match_id")],
        inverseJoinColumns = [JoinColumn(name = "expansion_game_id")],
    )
    var expansionsUsed: MutableSet<Game> = mutableSetOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)