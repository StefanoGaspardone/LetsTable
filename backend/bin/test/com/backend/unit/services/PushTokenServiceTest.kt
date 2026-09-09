package com.backend.unit.services

import com.backend.exceptions.UserNotFoundException
import com.backend.models.dtos.RegisterPushTokenRequest
import com.backend.models.entities.PushToken
import com.backend.models.entities.User
import com.backend.models.enums.UserRole
import com.backend.repositories.PushTokenRepository
import com.backend.repositories.UserRepository
import com.backend.services.PushTokenService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PushTokenServiceTest {

    @Mock
    private lateinit var pushTokenRepository: PushTokenRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @InjectMocks
    private lateinit var pushTokenService: PushTokenService

    private val userId: UUID = UUID.randomUUID()
    private val sampleUser = User(
        id = userId,
        username = "stefano",
        email = "stefano@example.com",
        passwordHash = "hash",
        role = UserRole.USER
    )

    private val tokenString = "ExponentPushToken[xxxxxxxxxxxxxx]"
    private val deviceName = "iPhone di Stefano"

    @Nested
    @DisplayName("register")
    inner class RegisterTests {

        @Test
        fun `should update existing token when token already exists in repository`() {
            val request = RegisterPushTokenRequest(token = tokenString, deviceName = deviceName)
            val existingToken = PushToken(
                id = UUID.randomUUID(),
                user = sampleUser,
                token = tokenString,
                deviceName = "Old Device"
            )

            val tokenCaptor = ArgumentCaptor.forClass(PushToken::class.java)

            `when`(pushTokenRepository.findByToken(tokenString)).thenReturn(Optional.of(existingToken))
            `when`(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser))
            `when`(pushTokenRepository.save(tokenCaptor.capture())).thenReturn(existingToken)

            pushTokenService.register(userId, request)

            verify(pushTokenRepository).findByToken(tokenString)
            verify(userRepository).findById(userId)
            verify(pushTokenRepository).save(existingToken)

            val savedToken = tokenCaptor.value
            assertThat(savedToken.deviceName).isEqualTo(deviceName)
            assertThat(savedToken.user.id).isEqualTo(userId)
        }

        @Test
        fun `should save new push token when token does not exist`() {
            val request = RegisterPushTokenRequest(token = tokenString, deviceName = deviceName)
            val tokenCaptor = ArgumentCaptor.forClass(PushToken::class.java)

            `when`(pushTokenRepository.findByToken(tokenString)).thenReturn(Optional.empty())
            `when`(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser))

            pushTokenService.register(userId, request)

            verify(pushTokenRepository).findByToken(tokenString)
            verify(userRepository).findById(userId)
            verify(pushTokenRepository).save(tokenCaptor.capture())

            val savedToken = tokenCaptor.value
            assertThat(savedToken.token).isEqualTo(tokenString)
            assertThat(savedToken.deviceName).isEqualTo(deviceName)
            assertThat(savedToken.user).isEqualTo(sampleUser)
        }

        @Test
        fun `should throw UserNotFoundException when user does not exist on new registration`() {
            val request = RegisterPushTokenRequest(token = tokenString, deviceName = deviceName)

            `when`(pushTokenRepository.findByToken(tokenString)).thenReturn(Optional.empty())
            `when`(userRepository.findById(userId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                pushTokenService.register(userId, request)
            }.isInstanceOf(UserNotFoundException::class.java)

            verify(pushTokenRepository, never()).save(org.mockito.ArgumentMatchers.any())
        }

        @Test
        fun `should throw UserNotFoundException when user does not exist on updating existing token`() {
            val request = RegisterPushTokenRequest(token = tokenString, deviceName = deviceName)
            val existingToken = PushToken(
                id = UUID.randomUUID(),
                user = sampleUser,
                token = tokenString,
                deviceName = "Old Device"
            )

            `when`(pushTokenRepository.findByToken(tokenString)).thenReturn(Optional.of(existingToken))
            `when`(userRepository.findById(userId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                pushTokenService.register(userId, request)
            }.isInstanceOf(UserNotFoundException::class.java)

            verify(pushTokenRepository, never()).save(org.mockito.ArgumentMatchers.any())
        }
    }

    @Nested
    @DisplayName("unregister")
    inner class UnregisterTests {

        @Test
        fun `should delete token by token string successfully`() {
            pushTokenService.unregister(tokenString)

            verify(pushTokenRepository).deleteByToken(tokenString)
        }

        @Test
        fun `should rethrow exception when repository fails during unregister`() {
            doThrow(RuntimeException("Database connection failed"))
                .`when`(pushTokenRepository).deleteByToken(tokenString)

            assertThatThrownBy {
                pushTokenService.unregister(tokenString)
            }.isInstanceOf(RuntimeException::class.java)

            verify(pushTokenRepository).deleteByToken(tokenString)
        }
    }
}