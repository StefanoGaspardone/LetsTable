package com.backend.services

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

@Service
class JwtService(
    @Value($$"${jwt.secret}") private val jwtSecret: String,
    @Value($$"${jwt.access-token-ttl-minutes}") private val ttlMinutes: Long,
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))

    fun generateAccessToken(userId: UUID, role: String): String {
        val now = Instant.now()
        val expiration = now.plus(ttlMinutes, ChronoUnit.MINUTES)

        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key, Jwts.SIG.HS256)
            .compact()

        logger.debug("\n\t[DEBUG] [jwt_service][generate_access_token] Generated access token for user {} with role {}", userId, role)
        return token
    }
}