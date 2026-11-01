package com.backend.models.enums

enum class NotificationChannel(val id: String) {
    FRIENDS("friends"),
    MATCHES("matches"),
    WISHLISTS("wishlists"),
}

enum class NotificationType(val channel: NotificationChannel, val categoryId: String? = null) {
    FRIEND_REQUEST(NotificationChannel.FRIENDS, "friend_request"),
    FRIEND_ACCEPTED(NotificationChannel.FRIENDS),
    WISHLIST_MEMBER_ADDED(NotificationChannel.WISHLISTS),
}