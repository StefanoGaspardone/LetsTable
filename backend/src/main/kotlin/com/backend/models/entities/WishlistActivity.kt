package com.backend.models.entities

import com.backend.models.enums.WishlistActivityAction
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "wishlist_activities")
class WishlistActivity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wishlist_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var wishlist: Wishlist,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var actor: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var game: Game,

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10)
    var action: WishlistActivityAction,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "notified_at")
    var notifiedAt: Instant? = null,
)