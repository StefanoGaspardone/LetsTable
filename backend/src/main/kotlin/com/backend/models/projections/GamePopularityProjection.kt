package com.backend.models.projections

import java.util.UUID

interface GamePopularityProjection {
    val gameId: UUID
    val gameName: String
    val count: Long
}