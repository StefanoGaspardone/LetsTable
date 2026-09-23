package com.backend.unit.schedulers

import com.backend.models.enums.AccountStatus
import com.backend.repositories.UserRepository
import com.backend.schedulers.UserCleanupScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(MockitoExtension::class)
class UserCleanupSchedulerTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @InjectMocks
    private lateinit var userCleanupScheduler: UserCleanupScheduler

    @Nested
    @DisplayName("deleteStaleInactiveAccounts")
    inner class DeleteStaleInactiveAccountsTests {

        @Test
        fun `should call userRepository with INACTIVE status`() {
            whenever(userRepository.deleteAllByAccountStatusAndCreatedAtBefore(any(), any())).thenReturn(0L)

            userCleanupScheduler.deleteStaleInactiveAccounts()

            verify(userRepository).deleteAllByAccountStatusAndCreatedAtBefore(eq(AccountStatus.INACTIVE), any())
        }

        @Test
        fun `should pass a cutoff of approximately 7 days ago`() {
            val cutoffCaptor = argumentCaptor<Instant>()
            whenever(userRepository.deleteAllByAccountStatusAndCreatedAtBefore(any(), any())).thenReturn(0L)

            val before = Instant.now().minus(7, ChronoUnit.DAYS)
            userCleanupScheduler.deleteStaleInactiveAccounts()
            val after = Instant.now().minus(7, ChronoUnit.DAYS)

            verify(userRepository).deleteAllByAccountStatusAndCreatedAtBefore(eq(AccountStatus.INACTIVE), cutoffCaptor.capture())
            val capturedCutoff = cutoffCaptor.firstValue
            assertThat(capturedCutoff).isBetween(before.minusSeconds(5), after.plusSeconds(5))
        }

        @Test
        fun `should swallow exception when userRepository throws and not propagate it`() {
            whenever(userRepository.deleteAllByAccountStatusAndCreatedAtBefore(any(), any()))
                .thenThrow(RuntimeException("Database unavailable"))

            userCleanupScheduler.deleteStaleInactiveAccounts()

            verify(userRepository).deleteAllByAccountStatusAndCreatedAtBefore(eq(AccountStatus.INACTIVE), any())
        }
    }
}