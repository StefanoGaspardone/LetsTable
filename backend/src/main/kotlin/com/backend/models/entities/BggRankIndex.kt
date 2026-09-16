package com.backend.models.entities

import jakarta.persistence.*

@Entity
@Table(name = "bgg_rank_index")
class BggRankIndex(
    @Id
    @Column(name = "bgg_id", nullable = false)
    var bggId: Long,

    @Column(name = "rank", nullable = false)
    var rank: Int,
)