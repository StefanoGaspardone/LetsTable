package com.backend.models.projections

import java.time.LocalDate

interface MatchDayCountProjection {
    val playedAt: LocalDate
    val matchCount: Long
}