package com.backend.models.projections

import java.time.Instant
import java.util.UUID

interface FinishedMatchProjection {
    val playedAt: Instant
    val won: Boolean
    val gameId: UUID
}