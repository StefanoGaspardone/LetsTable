package com.backend.unit.services

import com.backend.exceptions.UserNotFoundException
import com.backend.models.dtos.RegisterPushTokenRequest
import com.backend.models.entities.PushToken
import com.backend.models.entities.User
import com.backend.repositories.PushTokenRepository
import com.backend.repositories.UserRepository
import com.backend.services.PushTokenService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class PushTokenServiceTest {

    private lateinit var pushTokenRepository: PushTokenRepository
    private lateinit var userRepository: UserRepository
    private lateinit var service: PushTokenService

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val user = User(id = userId, username = "stefano", email = "stefano@example.com", passwordHash = "hash")
    private val otherUser = User(id = otherUserId, username = "marco", email = "marco@example.com", passwordHash = "hash")
    private val savedSlot = slot<PushToken>()

    companion object {
        private const val TOKEN = "fcm-token-abc"
        private const val DEVICE_NAME = "Pixel di Stefano"
    }

    @BeforeEach
    fun setUp() {
        pushTokenRepository = mockk(relaxUnitFun = true)
        userRepository = mockk()

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { pushTokenRepository.findByToken(TOKEN) } returns Optional.empty()
        every { pushTokenRepository.save(capture(savedSlot)) } answers { firstArg() }

        service = PushTokenService(pushTokenRepository, userRepository)
    }

    private fun request(deviceName: String? = DEVICE_NAME) = RegisterPushTokenRequest(token = TOKEN, deviceName = deviceName)

    @Nested
    @DisplayName("register")
    inner class RegisterTests {

        @Test
        fun `should save a new token for the user`() {
            service.register(userId, request())

            val saved = savedSlot.captured
            assertThat(saved.id).isNull()
            assertThat(saved.token).isEqualTo(TOKEN)
            assertThat(saved.deviceName).isEqualTo(DEVICE_NAME)
            assertThat(saved.user).isSameAs(user)
        }

        @Test
        fun `should save a new token without a device name`() {
            service.register(userId, request(deviceName = null))

            assertThat(savedSlot.captured.deviceName).isNull()
        }

        @Test
        fun `should update the device name of a token already registered by the user`() {
            val existing = PushToken(id = UUID.randomUUID(), user = user, token = TOKEN, deviceName = "Vecchio telefono")
            every { pushTokenRepository.findByToken(TOKEN) } returns Optional.of(existing)

            service.register(userId, request())

            assertThat(savedSlot.captured).isSameAs(existing)
            assertThat(existing.deviceName).isEqualTo(DEVICE_NAME)
            assertThat(existing.user).isSameAs(user)
        }

        @Test
        fun `should move a token registered by another user to the current one`() {
            val existing = PushToken(id = UUID.randomUUID(), user = otherUser, token = TOKEN, deviceName = DEVICE_NAME)
            every { pushTokenRepository.findByToken(TOKEN) } returns Optional.of(existing)

            service.register(userId, request())

            assertThat(savedSlot.captured).isSameAs(existing)
            assertThat(existing.user).isSameAs(user)
            verify(exactly = 1) { pushTokenRepository.save(any()) }
        }

        @Test
        fun `should throw UserNotFoundException without touching the tokens when the user does not exist`() {
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { service.register(userId, request()) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { pushTokenRepository.findByToken(any()) }
            verify(exactly = 0) { pushTokenRepository.save(any()) }
        }

        @Test
        fun `should not steal a token for a user that does not exist`() {
            val existing = PushToken(id = UUID.randomUUID(), user = otherUser, token = TOKEN, deviceName = DEVICE_NAME)
            every { pushTokenRepository.findByToken(TOKEN) } returns Optional.of(existing)
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { service.register(userId, request()) }
                .isInstanceOf(UserNotFoundException::class.java)

            assertThat(existing.user).isSameAs(otherUser)
        }

        @Test
        fun `should propagate a repository failure`() {
            every { pushTokenRepository.save(any()) } throws RuntimeException("Database error")

            assertThatThrownBy { service.register(userId, request()) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Database error")
        }
    }

    @Nested
    @DisplayName("unregister")
    inner class UnregisterTests {

        @Test
        fun `should delete the token only for the current user`() {
            service.unregister(userId, TOKEN)

            verify(exactly = 1) { pushTokenRepository.deleteByTokenAndUserId(TOKEN, userId) }
        }

        @Test
        fun `should propagate a repository failure`() {
            every { pushTokenRepository.deleteByTokenAndUserId(TOKEN, userId) } throws RuntimeException("Database error")

            assertThatThrownBy { service.unregister(userId, TOKEN) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Database error")
        }
    }
}