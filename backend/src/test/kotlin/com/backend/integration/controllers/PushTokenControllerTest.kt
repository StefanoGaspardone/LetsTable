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
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

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

    companion object {
        private const val BASE_URL = "/api/v1/push-tokens"
    }

    @AfterEach
    fun cleanUp() {
        pushTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun persistUser(username: String = "stefano", role: UserRole = UserRole.USER): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = role,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

    private fun authHeader(userId: UUID, role: UserRole = UserRole.USER): String =
        "Bearer ${jwtService.generateAccessToken(userId, role.name)}"

    private fun persistPushToken(user: User, token: String, deviceName: String? = null): PushToken =
        pushTokenRepository.saveAndFlush(PushToken(user = user, token = token, deviceName = deviceName))

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, user: User?): ResultActions =
        mockMvc.perform(request.apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it.id!!, it.role)) } })

    private fun register(user: User?, body: String): ResultActions =
        perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body), user)

    private fun unregister(user: User?, token: String?): ResultActions =
        perform(delete(BASE_URL).apply { token?.let { param("token", it) } }, user)

    private fun tokensOf(user: User): List<String> =
        pushTokenRepository.findAll().filter { it.user.id == user.id }.map { it.token }

    @Nested
    @DisplayName("POST /api/v1/push-tokens")
    inner class RegisterTests {

        @Test
        fun `should register a new push token for the current user`() {
            val user = persistUser()

            register(user, """{"token":"ExponentPushToken[abc123]","deviceName":"Mario's Pixel 8"}""")
                .andExpect(status().isCreated)

            val saved = pushTokenRepository.findByToken("ExponentPushToken[abc123]").orElseThrow()
            assertThat(saved.user.id).isEqualTo(user.id)
            assertThat(saved.deviceName).isEqualTo("Mario's Pixel 8")
        }

        @Test
        fun `should register without a deviceName since it is optional`() {
            val user = persistUser()

            register(user, """{"token":"ExponentPushToken[noname]"}""").andExpect(status().isCreated)

            assertThat(pushTokenRepository.findByToken("ExponentPushToken[noname]").orElseThrow().deviceName).isNull()
        }

        @Test
        fun `should keep a single row when the same user registers the same token again`() {
            val user = persistUser()
            persistPushToken(user, token = "ExponentPushToken[same]", deviceName = "Old Name")

            register(user, """{"token":"ExponentPushToken[same]","deviceName":"New Name"}""").andExpect(status().isCreated)

            assertThat(pushTokenRepository.findAll()).hasSize(1)
            assertThat(pushTokenRepository.findByToken("ExponentPushToken[same]").orElseThrow().deviceName).isEqualTo("New Name")
        }

        @Test
        fun `should keep one token per device when a user has many devices`() {
            val user = persistUser()

            register(user, """{"token":"ExponentPushToken[phone]"}""").andExpect(status().isCreated)
            register(user, """{"token":"ExponentPushToken[tablet]"}""").andExpect(status().isCreated)

            assertThat(tokensOf(user)).containsExactlyInAnyOrder("ExponentPushToken[phone]", "ExponentPushToken[tablet]")
        }

        @Test
        fun `should reassign an existing token to a different user`() {
            val originalOwner = persistUser(username = "original")
            val newOwner = persistUser(username = "new-owner")
            persistPushToken(originalOwner, token = "shared-device-token", deviceName = "Old Device Name")

            register(newOwner, """{"token":"shared-device-token","deviceName":"New Device Name"}""")
                .andExpect(status().isCreated)

            val updated = pushTokenRepository.findByToken("shared-device-token").orElseThrow()
            assertThat(updated.user.id).isEqualTo(newOwner.id)
            assertThat(updated.deviceName).isEqualTo("New Device Name")
            assertThat(pushTokenRepository.findAll()).hasSize(1)
            assertThat(tokensOf(originalOwner)).isEmpty()
        }

        @Test
        fun `should return 400 when token is blank`() {
            register(persistUser(), """{"token":"   "}""").andExpect(status().isBadRequest)

            assertThat(pushTokenRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 when token is missing entirely`() {
            register(persistUser(), """{"deviceName":"Some Device"}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when body is missing`() {
            perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON), persistUser()).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when body is not valid JSON`() {
            register(persistUser(), """{"token":""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 when the current user does not exist`() {
            mockMvc.perform(
                post(BASE_URL)
                    .header(HttpHeaders.AUTHORIZATION, authHeader(UUID.randomUUID()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"ExponentPushToken[ghost]"}""")
            ).andExpect(status().isNotFound)

            assertThat(pushTokenRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            register(null, """{"token":"some-token"}""").andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/push-tokens")
    inner class UnregisterTests {

        @Test
        fun `should unregister an existing push token`() {
            val user = persistUser()
            persistPushToken(user, token = "token-to-remove")

            unregister(user, "token-to-remove").andExpect(status().isNoContent)

            assertThat(pushTokenRepository.findByToken("token-to-remove")).isEmpty()
        }

        @Test
        fun `should remove only the given token and keep the user's other devices`() {
            val user = persistUser()
            persistPushToken(user, token = "phone-token")
            persistPushToken(user, token = "tablet-token")

            unregister(user, "phone-token").andExpect(status().isNoContent)

            assertThat(tokensOf(user)).containsExactly("tablet-token")
        }

        @Test
        fun `should return 204 even when the token does not exist`() {
            unregister(persistUser(), "nonexistent-token").andExpect(status().isNoContent)
        }

        @Test
        fun `should not remove a token owned by another user`() {
            val user = persistUser(username = "user")
            val otherUser = persistUser(username = "other")
            persistPushToken(otherUser, token = "other-users-token")

            unregister(user, "other-users-token").andExpect(status().isNoContent)

            val stillThere = pushTokenRepository.findByToken("other-users-token").orElseThrow()
            assertThat(stillThere.user.id).isEqualTo(otherUser.id)
        }

        @Test
        fun `should return 400 when token param is blank`() {
            unregister(persistUser(), "").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when token param is missing`() {
            unregister(persistUser(), null).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            unregister(null, "some-token").andExpect(status().isForbidden)
        }
    }
}