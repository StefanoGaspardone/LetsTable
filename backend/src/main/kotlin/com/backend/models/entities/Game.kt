package com.backend.models.entities

import com.backend.utils.ExpansionRefListConverter
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "games")
class Game(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "bgg_id", nullable = false, unique = true)
    var bggId: Long,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "year_published", nullable = true)
    var yearPublished: Int? = null,

    @Column(name = "thumbnail_url", nullable = true)
    var thumbnailUrl: String? = null,

    @Column(name = "image_url", nullable = true)
    var imageUrl: String? = null,

    @Column(name = "min_players", nullable = true)
    var minPlayers: Int? = null,

    @Column(name = "max_players", nullable = true)
    var maxPlayers: Int? = null,

    @Column(name = "playing_time_minutes", nullable = true)
    var playingTimeMinutes: Int? = null,

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "last_synced_at", nullable = false)
    var lastSyncedAt: Instant = Instant.now(),

    @Column(name = "best_with", nullable = true)
    var bestWith: String? = null,

    @Column(name = "recommended_with", nullable = true)
    var recommendedWith: String? = null,

    @Convert(converter = ExpansionRefListConverter::class)
    @Column(name = "expansion_refs", nullable = true, columnDefinition = "TEXT")
    var expansionRefs: List<ExpansionRef> = emptyList(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)