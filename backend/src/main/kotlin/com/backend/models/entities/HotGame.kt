package com.backend.models.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "hot_games")
class HotGame(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "bgg_id", nullable = false)
    var bggId: Long,

    @Column(name = "rank", nullable = false)
    var rank: Int,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "thumbnail_url", nullable = true)
    var thumbnailUrl: String? = null,

    @Column(name = "year_published", nullable = true)
    var yearPublished: Int? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)