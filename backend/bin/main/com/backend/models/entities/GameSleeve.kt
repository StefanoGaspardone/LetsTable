package com.backend.models.entities

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "game_sleeves")
class GameSleeve(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    var game: Game,

    @Column(name = "name", nullable = true)
    var name: String? = null,

    @Column(name = "height", nullable = true)
    var height: Double? = null,

    @Column(name = "width", nullable = true)
    var width: Double? = null,

    @Column(name = "quantity", nullable = true)
    var quantity: Int? = null,

    @Column(name = "quantity_note", nullable = true)
    var quantityNote: String? = null,
)