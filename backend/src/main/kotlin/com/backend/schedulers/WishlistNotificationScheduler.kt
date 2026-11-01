package com.backend.schedulers

import com.backend.services.WishlistNotificationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WishlistNotificationScheduler(
    private val wishlistNotificationService: WishlistNotificationService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun flushWishlistNotifications(): Int {
        logger.info("\n\t[INFO] [wishlist_notification_scheduler][flush_wishlist_notifications] Triggering wishlist notifications flush")
        return wishlistNotificationService.flushPendingNotifications()
    }
}