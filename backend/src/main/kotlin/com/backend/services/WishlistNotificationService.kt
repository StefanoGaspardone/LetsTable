package com.backend.services

import com.backend.models.entities.WishlistActivity
import com.backend.models.enums.NotificationType
import com.backend.models.enums.WishlistActivityAction
import com.backend.repositories.WishlistActivityRepository
import com.backend.repositories.WishlistMemberRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class WishlistNotificationService(
    private val wishlistActivityRepository: WishlistActivityRepository,
    private val wishlistMemberRepository: WishlistMemberRepository,
    private val pushNotificationService: PushNotificationService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val QUIET_PERIOD: Duration = Duration.ofMinutes(2)
        private val RETENTION: Duration = Duration.ofDays(7)
        private const val MAX_LISTED_GAMES = 3
    }

    private data class NetChanges(val added: List<String>, val removed: List<String>)

    @Transactional
    fun flushPendingNotifications(): Int {
        logger.debug("\n\t[DEBUG] [wishlist_notification_service][flush_pending_notifications] Flushing pending wishlist activities")

        try {
            val now = Instant.now()
            val quietCutoff = now.minus(QUIET_PERIOD)

            val readyGroups = wishlistActivityRepository.findAllPending()
                .sortedBy { it.createdAt }
                .groupBy { it.wishlist.id!! to it.actor.id!! }
                .filterValues { group -> group.maxOf { it.createdAt } <= quietCutoff }

            var sent = 0

            readyGroups.forEach { (key, activities) ->
                val (wishlistId, actorId) = key

                if(notifyGroup(wishlistId, actorId, activities)) {
                    sent++
                }

                wishlistActivityRepository.markNotified(activities.mapNotNull { it.id }, now)
            }

            val purged = wishlistActivityRepository.deleteNotifiedBefore(now.minus(RETENTION))

            logger.info("\n\t[INFO] [wishlist_notification_service][flush_pending_notifications] Sent {} notification(s) for {} group(s), purged {} old activities", sent, readyGroups.size, purged)
            return sent
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_notification_service][flush_pending_notifications] Error flushing wishlist activities: {}", e.message, e)
            throw e
        }
    }

    private fun notifyGroup(wishlistId: UUID, actorId: UUID, activities: List<WishlistActivity>): Boolean {
        val wishlist = activities.first().wishlist
        val changes = computeNetChanges(activities)

        if(!wishlist.isShared || (changes.added.isEmpty() && changes.removed.isEmpty())) {
            return false
        }

        val recipients = (wishlistMemberRepository.findAllByWishlistId(wishlistId).map { it.user.id!! } + wishlist.owner.id!!)
            .toSet() - actorId

        if(recipients.isEmpty()) {
            return false
        }

        pushNotificationService.sendToUsers(
            userIds = recipients,
            type = NotificationType.WISHLIST_UPDATED,
            title = wishlist.name,
            body = buildBody(activities.first().actor.username, changes),
            data = mapOf("wishlistId" to wishlistId.toString()),
        )

        return true
    }

    private fun computeNetChanges(activities: List<WishlistActivity>): NetChanges {
        val netByGame = activities
            .groupBy { it.game.id }
            .values
            .mapNotNull { history ->
                val first = history.first().action
                val last = history.last().action
                if(first == last) history.last().game.name to last else null
            }

        return NetChanges(
            added = netByGame.filter { it.second == WishlistActivityAction.ADDED }.map { it.first },
            removed = netByGame.filter { it.second == WishlistActivityAction.REMOVED }.map { it.first },
        )
    }

    private fun buildBody(actor: String, changes: NetChanges): String =
        when {
            changes.removed.isEmpty() -> "$actor ha aggiunto ${formatNames(changes.added)}"
            changes.added.isEmpty() -> "$actor ha rimosso ${formatNames(changes.removed)}"
            else -> "$actor ha aggiunto ${formatCount(changes.added.size)} e rimosso ${formatCount(changes.removed.size)}"
        }

    private fun formatNames(names: List<String>): String =
        when {
            names.size == 1 -> names.first()
            names.size <= MAX_LISTED_GAMES -> "${names.dropLast(1).joinToString(", ")} e ${names.last()}"
            else -> "${names.take(2).joinToString(", ")} e altri ${names.size - 2} giochi"
        }

    private fun formatCount(count: Int): String =
        if(count == 1) "1 gioco" else "$count giochi"
}