package com.backend.unit.schedulers

import com.backend.models.enums.AccountStatus
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UserRepository
import com.backend.schedulers.UserCleanupScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class UserCleanupSchedulerTest {

    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var scheduler: UserCleanupScheduler

    private val cutoffSlot = slot<Instant>()

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        refreshTokenRepository = mockk(relaxed = true)

        every { userRepository.deleteAllByAccountStatusAndCreatedAtBefore(any(), capture(cutoffSlot)) } returns 3L

        scheduler = UserCleanupScheduler(userRepository, refreshTokenRepository)
    }

    @Test
    fun `should return the number of deleted accounts`() {
        assertThat(scheduler.deleteStaleInactiveAccounts()).isEqualTo(3L)
    }

    @Test
    fun `should delete only the accounts never activated`() {
        scheduler.deleteStaleInactiveAccounts()

        verify(exactly = 1) { userRepository.deleteAllByAccountStatusAndCreatedAtBefore(AccountStatus.INACTIVE, any()) }
    }

    @Test
    fun `should delete the accounts created more than seven days ago`() {
        val before = Instant.now()

        scheduler.deleteStaleInactiveAccounts()

        val after = Instant.now()
        assertThat(cutoffSlot.captured).isBetween(before.minus(Duration.ofDays(7)), after.minus(Duration.ofDays(7)))
    }

    @Test
    fun `should remove the refresh tokens revoked or expired at the same instant`() {
        val nowSlot = slot<Instant>()

        scheduler.deleteStaleInactiveAccounts()

        verify(exactly = 1) { refreshTokenRepository.deleteRevokedOrExpired(capture(nowSlot)) }
        assertThat(cutoffSlot.captured).isEqualTo(nowSlot.captured.minus(Duration.ofDays(7)))
    }

    @Test
    fun `should delete the accounts before cleaning the tokens`() {
        scheduler.deleteStaleInactiveAccounts()

        verifyOrder {
            userRepository.deleteAllByAccountStatusAndCreatedAtBefore(any(), any())
            refreshTokenRepository.deleteRevokedOrExpired(any())
        }
    }

    @Test
    fun `should return zero when there is nothing to delete`() {
        every { userRepository.deleteAllByAccountStatusAndCreatedAtBefore(any(), any()) } returns 0L

        assertThat(scheduler.deleteStaleInactiveAccounts()).isZero()

        verify(exactly = 1) { refreshTokenRepository.deleteRevokedOrExpired(any()) }
    }

    @Test
    fun `should not clean the tokens when deleting the accounts fails`() {
        every { userRepository.deleteAllByAccountStatusAndCreatedAtBefore(any(), any()) } throws RuntimeException("Database error")

        assertThatThrownBy { scheduler.deleteStaleInactiveAccounts() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Database error")

        verify(exactly = 0) { refreshTokenRepository.deleteRevokedOrExpired(any()) }
    }

    @Test
    fun `should propagate a failure while cleaning the tokens`() {
        every { refreshTokenRepository.deleteRevokedOrExpired(any()) } throws RuntimeException("Database error")

        assertThatThrownBy { scheduler.deleteStaleInactiveAccounts() }
            .isInstanceOf(RuntimeException::class.java)
    }
}