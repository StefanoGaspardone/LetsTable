package com.backend.security

import java.util.UUID

data class JwtUserPrincipal(
    val userId: UUID,
    val role: String,
)