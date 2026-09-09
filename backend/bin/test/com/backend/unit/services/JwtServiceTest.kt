package com.backend.unit.services

import com.backend.services.JwtService
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.io.Encoders
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.KeyGenerator

class JwtServiceTest {

    private lateinit var validBase64Secret: String
    private val defaultTtlMinutes: Long = 15L

    @BeforeEach
    fun setUp() {
        val keyGen = KeyGenerator.getInstance("HmacSHA256")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()
        validBase64Secret = Encoders.BASE64.encode(secretKey.encoded)
    }

    @Nested
    @DisplayName("Service initialization")
    inner class InitializationTests {

        @Test
        fun `should initialize correctly when valid base64 secret is provided`() {
            val jwtService = JwtService(validBase64Secret, defaultTtlMinutes)
            assertThat(jwtService).isNotNull
        }

        @Test
        fun `should throw exception when secret is not valid base64`() {
            val invalidBase64Secret = "???NotValidBase64!!!"

            assertThatThrownBy { JwtService(invalidBase64Secret, defaultTtlMinutes) }
                .isInstanceOf(Exception::class.java)
        }

        @Test
        fun `should throw exception when secret key length is too short for HS256`() {
            val shortBase64Secret = Encoders.BASE64.encode("short".toByteArray())

            assertThatThrownBy { JwtService(shortBase64Secret, defaultTtlMinutes) }
                .isInstanceOf(WeakKeyException::class.java)
        }
    }

    @Nested
    @DisplayName("generateAccessToken")
    inner class GenerateAccessTokenTests {

        @Test
        fun `should generate a valid JWT token with correct claims and expiration`() {
            val jwtService = JwtService(validBase64Secret, defaultTtlMinutes)
            val userId = UUID.randomUUID()
            val role = "ROLE_USER"

            val beforeGeneration = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            val token = jwtService.generateAccessToken(userId, role)
            val afterGeneration = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1)

            assertThat(token).isNotBlank()

            val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(validBase64Secret))
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            assertThat(claims.subject).isEqualTo(userId.toString())
            assertThat(claims["role"]).isEqualTo(role)

            val issuedAt = claims.issuedAt.toInstant()
            val expiration = claims.expiration.toInstant()

            assertThat(issuedAt).isBetween(beforeGeneration, afterGeneration)
            val expectedExpiration = issuedAt.plus(defaultTtlMinutes, ChronoUnit.MINUTES)
            assertThat(expiration).isEqualTo(expectedExpiration)
        }

        @ParameterizedTest
        @ValueSource(strings = ["ROLE_ADMIN", "ROLE_USER", "GUEST", "", "   "])
        fun `should handle various role string values correctly`(role: String) {
            val jwtService = JwtService(validBase64Secret, defaultTtlMinutes)
            val userId = UUID.randomUUID()

            val token = jwtService.generateAccessToken(userId, role)

            val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(validBase64Secret))
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            assertThat(claims["role"]).isEqualTo(role)
        }

        @Test
        fun `should calculate correct expiration date with 0 minutes TTL (edge case)`() {
            val jwtService = JwtService(validBase64Secret, 0L)
            val userId = UUID.randomUUID()
            val role = "ROLE_USER"

            val token = jwtService.generateAccessToken(userId, role)

            val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(validBase64Secret))

            val claims = Jwts.parser()
                .clockSkewSeconds(60)
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            assertThat(claims.expiration).isEqualTo(claims.issuedAt)
        }

        @Test
        fun `should calculate correct expiration date with negative TTL`() {
            val jwtService = JwtService(validBase64Secret, -5L)
            val userId = UUID.randomUUID()
            val role = "ROLE_USER"

            val token = jwtService.generateAccessToken(userId, role)

            val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(validBase64Secret))

            assertThatThrownBy {
                Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
            }.isInstanceOf(ExpiredJwtException::class.java)
        }

        @Test
        fun `should produce unique tokens for different user IDs`() {
            val jwtService = JwtService(validBase64Secret, defaultTtlMinutes)
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val role = "ROLE_USER"

            val token1 = jwtService.generateAccessToken(userId1, role)
            val token2 = jwtService.generateAccessToken(userId2, role)

            assertThat(token1).isNotEqualTo(token2)
        }
    }
}