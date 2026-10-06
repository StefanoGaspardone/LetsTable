package com.backend.models.projections

import java.time.Instant
import java.util.UUID

interface GameMatchStatsProjection {
    val gameId: UUID
    val bggId: Long
    val gameName: String
    val gameThumbnailUrl: String?
    val matchCount: Long
    val totalMinutes: Long
    val avgMinutes: Double
    val wins: Long
    val winRate: Double
    val lastPlayedAt: Instant
}