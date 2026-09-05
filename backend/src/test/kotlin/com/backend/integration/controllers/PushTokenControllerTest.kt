package com.backend.integration.controllers

import com.backend.models.entities.PushToken
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.PushTokenRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class PushTokenControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var pushTokenRepository: PushTokenRepository

    @Autowired
    private lateinit var jwtService: JwtService

    private fun persistUser(username: String = "stefano"): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = UserRole.USER,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun persistPushToken(user: User, token: String, deviceName: String? = null): PushToken =
        pushTokenRepository.saveAndFlush(PushToken(user = user, token = token, deviceName = deviceName))

    @AfterEach
    fun cleanUp() {
        pushTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/push-tokens
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/push-tokens")
    inner class RegisterTests {

        @Test
        fun `should register a new push token for the current user`() {
            val user = persistUser()
            val payload = """{"token":"ExponentPushToken[abc123]","deviceName":"Mario's Pixel 8"}"""

            mockMvc.perform(
                post("/api/v1/push-tokens")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isCreated)

            val saved = pushTokenRepository.findByToken("ExponentPushToken[abc123]")
            assertThat(saved).isPresent
            assertThat(saved.get().user.id).isEqualTo(user.id)
            assertThat(saved.get().deviceName).isEqualTo("Mario's Pixel 8")
        }

        @Test
        fun `should reassign an existing token to a different user`() {
            val originalOwner = persistUser(username = "original")
            val newOwner = persistUser(username = "new-owner")
            persistPushToken(originalOwner, token = "shared-device-token", deviceName = "Old Device Name")

            val payload = """{"token":"shared-device-token","deviceName":"New Device Name"}"""

            mockMvc.perform(
                post("/api/v1/push-tokens")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(newOwner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isCreated)

            val updated = pushTokenRepository.findByToken("shared-device-token").orElseThrow()
            assertThat(updated.user.id).isEqualTo(newOwner.id)
            assertThat(updated.deviceName).isEqualTo("New Device Name")

            val allTokens = pushTokenRepository.findAll()
            assertThat(allTokens).hasSize(1)
        }

        @Test
        fun `should register without a deviceName since it is optional`() {
            val user = persistUser()
            val payload = """{"token":"ExponentPushToken[noname]"}"""

            mockMvc.perform(
                post("/api/v1/push-tokens")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isCreated)

            val saved = pushTokenRepository.findByToken("ExponentPushToken[noname]").orElseThrow()
            assertThat(saved.deviceName).isNull()
        }

        @Test
        fun `should return 400 when token is blank`() {
            val user = persistUser()
            val payload = """{"token":""}"""

            mockMvc.perform(
                post("/api/v1/push-tokens")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when token is missing entirely`() {
            val user = persistUser()
            val payload = """{"deviceName":"Some Device"}"""

            mockMvc.perform(
                post("/api/v1/push-tokens")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val payload = """{"token":"some-token"}"""

            mockMvc.perform(
                post("/api/v1/push-tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/push-tokens
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/push-tokens")
    inner class UnregisterTests {

        @Test
        fun `should unregister an existing push token`() {
            val user = persistUser()
            persistPushToken(user, token = "token-to-remove")

            mockMvc.perform(
                delete("/api/v1/push-tokens")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("token", "token-to-remove")
            ).andExpect(status().isNoContent)

            assertThat(pushTokenRepository.findByToken("token-to-remove")).isEmpty()
        }

        @Test
        fun `should return 204 even when the token does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                delete("/api/v1/push-tokens")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("token", "nonexistent-token")
            ).andExpect(status().isNoContent)
        }

        @Test
        fun `should not remove other users' tokens`() {
            val user = persistUser(username = "user")
            val otherUser = persistUser(username = "other")
            persistPushToken(otherUser, token = "other-users-token")

            mockMvc.perform(
                delete("/api/v1/push-tokens")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("token", "some-different-token")
            ).andExpect(status().isNoContent)

            assertThat(pushTokenRepository.findByToken("other-users-token")).isPresent
        }

        @Test
        fun `should return 400 when token param is blank`() {
            val user = persistUser()

            mockMvc.perform(
                delete("/api/v1/push-tokens")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("token", "")
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            mockMvc.perform(
                delete("/api/v1/push-tokens")
                    .param("token", "some-token")
            ).andExpect(status().isForbidden)
        }
    }
}