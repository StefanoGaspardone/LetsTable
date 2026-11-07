package com.backend.unit.schedulers

import com.backend.schedulers.WishlistNotificationScheduler
import com.backend.services.WishlistNotificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WishlistNotificationSchedulerTest {

    private lateinit var wishlistNotificationService: WishlistNotificationService
    private lateinit var scheduler: WishlistNotificationScheduler

    @BeforeEach
    fun setUp() {
        wishlistNotificationService = mockk()
        scheduler = WishlistNotificationScheduler(wishlistNotificationService)
    }

    @Test
    fun `should flush the pending notifications and return how many were sent`() {
        every { wishlistNotificationService.flushPendingNotifications() } returns 4

        assertThat(scheduler.flushWishlistNotifications()).isEqualTo(4)

        verify(exactly = 1) { wishlistNotificationService.flushPendingNotifications() }
    }

    @Test
    fun `should return zero when nothing was sent`() {
        every { wishlistNotificationService.flushPendingNotifications() } returns 0

        assertThat(scheduler.flushWishlistNotifications()).isZero()
    }

    @Test
    fun `should propagate a failure of the flush`() {
        every { wishlistNotificationService.flushPendingNotifications() } throws RuntimeException("Database error")

        assertThatThrownBy { scheduler.flushWishlistNotifications() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Database error")
    }
}