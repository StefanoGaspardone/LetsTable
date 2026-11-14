package com.backend.models.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "game_images")
class GameImage(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    var game: Game,

    @Column(name = "bgg_image_id", nullable = false)
    var bggImageId: Long,

    @Column(name = "thumbnail_url", nullable = false, columnDefinition = "TEXT")
    var thumbnailUrl: String,

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    var imageUrl: String,

    @Column(name = "caption", nullable = true, columnDefinition = "TEXT")
    var caption: String? = null,

    @Column(name = "author", nullable = true, length = 255)
    var author: String? = null,

    @Column(name = "page_url", nullable = true, columnDefinition = "TEXT")
    var pageUrl: String? = null,

    @Column(name = "position", nullable = false)
    var position: Int,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)