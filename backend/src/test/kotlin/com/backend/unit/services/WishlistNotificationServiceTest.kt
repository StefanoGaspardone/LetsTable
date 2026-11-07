package com.backend.unit.services

import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistActivity
import com.backend.models.entities.WishlistMember
import com.backend.models.enums.NotificationType
import com.backend.models.enums.WishlistActivityAction
import com.backend.models.enums.WishlistActivityAction.ADDED
import com.backend.models.enums.WishlistActivityAction.REMOVED
import com.backend.repositories.WishlistActivityRepository
import com.backend.repositories.WishlistMemberRepository
import com.backend.services.PushNotificationService
import com.backend.services.WishlistNotificationService
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
import java.time.Duration
import java.time.Instant
import java.util.UUID

class WishlistNotificationServiceTest {

    private lateinit var wishlistActivityRepository: WishlistActivityRepository
    private lateinit var wishlistMemberRepository: WishlistMemberRepository
    private lateinit var pushNotificationService: PushNotificationService
    private lateinit var service: WishlistNotificationService

    private val owner = User(id = UUID.randomUUID(), username = "stefano", email = "stefano@example.com", passwordHash = "hash")
    private val marco = User(id = UUID.randomUUID(), username = "marco", email = "marco@example.com", passwordHash = "hash")
    private val anna = User(id = UUID.randomUUID(), username = "anna", email = "anna@example.com", passwordHash = "hash")

    private val catan = Game(id = UUID.randomUUID(), bggId = 13L, name = "Catan")
    private val azul = Game(id = UUID.randomUUID(), bggId = 230802L, name = "Azul")
    private val wingspan = Game(id = UUID.randomUUID(), bggId = 266192L, name = "Wingspan")
    private val arkNova = Game(id = UUID.randomUUID(), bggId = 342942L, name = "Ark Nova")

    private lateinit var wishlist: Wishlist
    private val wishlistId = UUID.randomUUID()

    private val recipientsSlot = slot<Collection<UUID>>()
    private val titleSlot = slot<String>()
    private val bodySlot = slot<String>()
    private val dataSlot = slot<Map<String, Any>>()

    private val readyAt: Instant = Instant.now().minus(Duration.ofMinutes(10))

    @BeforeEach
    fun setUp() {
        wishlistActivityRepository = mockk(relaxed = true)
        wishlistMemberRepository = mockk()
        pushNotificationService = mockk(relaxUnitFun = true)

        wishlist = wishlist(wishlistId, isShared = true, members = listOf(marco, anna))

        every { pushNotificationService.sendToUsers(capture(recipientsSlot), any(), capture(titleSlot), capture(bodySlot), capture(dataSlot)) } returns Unit

        service = WishlistNotificationService(wishlistActivityRepository, wishlistMemberRepository, pushNotificationService)
    }

    private fun wishlist(id: UUID, isShared: Boolean = true, members: List<User> = emptyList(), name: String = "Da comprare"): Wishlist {
        val result = mockk<Wishlist> {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
            every { this@mockk.isShared } returns isShared
            every { this@mockk.owner } returns this@WishlistNotificationServiceTest.owner
        }
        every { wishlistMemberRepository.findAllByWishlistId(id) } returns members.map { member -> mockk<WishlistMember> { every { user } returns member } }
        return result
    }

    private fun activity(
        game: Game,
        action: WishlistActivityAction,
        actor: User = marco,
        at: Instant = readyAt,
        target: Wishlist = wishlist,
    ): WishlistActivity = mockk {
        every { id } returns UUID.randomUUID()
        every { this@mockk.wishlist } returns target
        every { this@mockk.actor } returns actor
        every { this@mockk.game } returns game
        every { this@mockk.action } returns action
        every { createdAt } returns at
    }

    private fun stubPending(vararg activities: WishlistActivity) {
        every { wishlistActivityRepository.findAllPending() } returns activities.toList()
    }

    private fun bodyFor(vararg activities: WishlistActivity): String {
        stubPending(*activities)
        service.flushPendingNotifications()
        return bodySlot.captured
    }

    private fun verifyNotSent() = verify(exactly = 0) { pushNotificationService.sendToUsers(any(), any(), any(), any(), any()) }

    @Nested
    @DisplayName("quiet period")
    inner class QuietPeriodTests {

        @Test
        fun `should do nothing when there is no pending activity`() {
            stubPending()

            assertThat(service.flushPendingNotifications()).isZero()

            verifyNotSent()
            verify(exactly = 0) { wishlistActivityRepository.markNotified(any(), any()) }
        }

        @Test
        fun `should wait while the user is still editing the wishlist`() {
            stubPending(activity(catan, ADDED, at = Instant.now().minusSeconds(30)))

            assertThat(service.flushPendingNotifications()).isZero()

            verifyNotSent()
            verify(exactly = 0) { wishlistActivityRepository.markNotified(any(), any()) }
        }

        @Test
        fun `should wait for the last change of the group, not the first`() {
            stubPending(
                activity(catan, ADDED, at = Instant.now().minus(Duration.ofMinutes(30))),
                activity(azul, ADDED, at = Instant.now().minusSeconds(30)),
            )

            service.flushPendingNotifications()

            verifyNotSent()
        }

        @Test
        fun `should notify once the quiet period is over`() {
            stubPending(activity(catan, ADDED, at = Instant.now().minus(Duration.ofMinutes(3))))

            assertThat(service.flushPendingNotifications()).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("notification")
    inner class NotificationTests {

        @Test
        fun `should notify owner and members except the actor`() {
            stubPending(activity(catan, ADDED, actor = marco))

            service.flushPendingNotifications()

            assertThat(recipientsSlot.captured).containsExactlyInAnyOrder(owner.id, anna.id)
        }

        @Test
        fun `should not notify the owner about his own changes`() {
            stubPending(activity(catan, ADDED, actor = owner))

            service.flushPendingNotifications()

            assertThat(recipientsSlot.captured).containsExactlyInAnyOrder(marco.id, anna.id)
        }

        @Test
        fun `should use the wishlist name as title and link the wishlist`() {
            stubPending(activity(catan, ADDED))

            service.flushPendingNotifications()

            assertThat(titleSlot.captured).isEqualTo("Da comprare")
            assertThat(dataSlot.captured).isEqualTo(mapOf("wishlistId" to wishlistId.toString()))
            verify(exactly = 1) { pushNotificationService.sendToUsers(any(), NotificationType.WISHLIST_UPDATED, any(), any(), any()) }
        }

        @Test
        fun `should mark every activity of the group as notified`() {
            val activities = arrayOf(activity(catan, ADDED), activity(azul, ADDED), activity(azul, REMOVED))
            stubPending(*activities)
            val idsSlot = slot<List<UUID>>()

            service.flushPendingNotifications()

            verify { wishlistActivityRepository.markNotified(capture(idsSlot), any()) }
            assertThat(idsSlot.captured).containsExactlyInAnyOrderElementsOf(activities.map { it.id!! })
        }

        @Test
        fun `should send one notification per actor and per wishlist`() {
            val otherWishlist = wishlist(UUID.randomUUID(), members = listOf(marco, anna), name = "Natale")
            stubPending(
                activity(catan, ADDED, actor = marco),
                activity(azul, ADDED, actor = anna),
                activity(wingspan, ADDED, actor = marco, target = otherWishlist),
            )

            assertThat(service.flushPendingNotifications()).isEqualTo(3)

            verify(exactly = 3) { pushNotificationService.sendToUsers(any(), any(), any(), any(), any()) }
            verify(exactly = 3) { wishlistActivityRepository.markNotified(any(), any()) }
        }

        @Test
        fun `should not notify a private wishlist but still consume its activities`() {
            val privateWishlist = wishlist(UUID.randomUUID(), isShared = false)
            stubPending(activity(catan, ADDED, actor = owner, target = privateWishlist))

            assertThat(service.flushPendingNotifications()).isZero()

            verifyNotSent()
            verify(exactly = 1) { wishlistActivityRepository.markNotified(any(), any()) }
        }

        @Test
        fun `should not notify when nobody else follows the wishlist`() {
            val lonelyWishlist = wishlist(UUID.randomUUID(), members = emptyList())
            stubPending(activity(catan, ADDED, actor = owner, target = lonelyWishlist))

            assertThat(service.flushPendingNotifications()).isZero()

            verifyNotSent()
            verify(exactly = 1) { wishlistActivityRepository.markNotified(any(), any()) }
        }

        @Test
        fun `should notify only the ready groups`() {
            stubPending(
                activity(catan, ADDED, actor = marco),
                activity(azul, ADDED, actor = anna, at = Instant.now().minusSeconds(10)),
            )

            assertThat(service.flushPendingNotifications()).isEqualTo(1)

            assertThat(bodySlot.captured).startsWith("marco")
            verify(exactly = 1) { wishlistActivityRepository.markNotified(any(), any()) }
        }
    }

    @Nested
    @DisplayName("net changes")
    inner class NetChangeTests {

        @Test
        fun `should ignore a game added and removed again`() {
            stubPending(activity(catan, ADDED), activity(catan, REMOVED))

            assertThat(service.flushPendingNotifications()).isZero()

            verifyNotSent()
            verify(exactly = 1) { wishlistActivityRepository.markNotified(any(), any()) }
        }

        @Test
        fun `should ignore a game removed and added back`() {
            stubPending(activity(catan, REMOVED), activity(catan, ADDED))

            service.flushPendingNotifications()

            verifyNotSent()
        }

        @Test
        fun `should report a game added, removed and added again as added`() {
            assertThat(bodyFor(activity(catan, ADDED), activity(catan, REMOVED), activity(catan, ADDED))).isEqualTo("marco ha aggiunto Catan")
        }

        @Test
        fun `should report a game removed, added and removed again as removed`() {
            assertThat(bodyFor(activity(catan, REMOVED), activity(catan, ADDED), activity(catan, REMOVED))).isEqualTo("marco ha rimosso Catan")
        }

        @Test
        fun `should use the order in which the changes happened, not the order they are loaded`() {
            val body = bodyFor(
                activity(catan, REMOVED, at = readyAt.minusSeconds(60)),
                activity(catan, ADDED, at = readyAt),
                activity(catan, ADDED, at = readyAt.minusSeconds(120)),
            )

            assertThat(body).isEqualTo("marco ha aggiunto Catan")
        }

        @Test
        fun `should keep only the games that really changed`() {
            assertThat(bodyFor(activity(catan, ADDED), activity(catan, REMOVED), activity(azul, ADDED))).isEqualTo("marco ha aggiunto Azul")
        }
    }

    @Nested
    @DisplayName("message body")
    inner class BodyTests {

        @Test
        fun `should name a single added game`() {
            assertThat(bodyFor(activity(catan, ADDED))).isEqualTo("marco ha aggiunto Catan")
        }

        @Test
        fun `should name two added games`() {
            assertThat(bodyFor(activity(catan, ADDED), activity(azul, ADDED))).isEqualTo("marco ha aggiunto Catan e Azul")
        }

        @Test
        fun `should name three added games`() {
            assertThat(bodyFor(activity(catan, ADDED), activity(azul, ADDED), activity(wingspan, ADDED))).isEqualTo("marco ha aggiunto Catan, Azul e Wingspan")
        }

        @Test
        fun `should summarize more than three added games`() {
            assertThat(bodyFor(activity(catan, ADDED), activity(azul, ADDED), activity(wingspan, ADDED), activity(arkNova, ADDED)))
                .isEqualTo("marco ha aggiunto Catan, Azul e altri 2 giochi")
        }

        @Test
        fun `should name the removed games`() {
            assertThat(bodyFor(activity(catan, REMOVED), activity(azul, REMOVED))).isEqualTo("marco ha rimosso Catan e Azul")
        }

        @Test
        fun `should count the games when some are added and some removed`() {
            assertThat(bodyFor(activity(catan, ADDED), activity(azul, REMOVED), activity(wingspan, REMOVED)))
                .isEqualTo("marco ha aggiunto 1 gioco e rimosso 2 giochi")
        }

        @Test
        fun `should use the plural for more added games`() {
            assertThat(bodyFor(activity(catan, ADDED), activity(azul, ADDED), activity(wingspan, REMOVED)))
                .isEqualTo("marco ha aggiunto 2 giochi e rimosso 1 gioco")
        }
    }

    @Nested
    @DisplayName("bookkeeping")
    inner class BookkeepingTests {

        @Test
        fun `should purge the activities notified more than a week ago`() {
            stubPending(activity(catan, ADDED))
            val notifiedAtSlot = slot<Instant>()
            val purgeSlot = slot<Instant>()

            service.flushPendingNotifications()

            verify { wishlistActivityRepository.markNotified(any(), capture(notifiedAtSlot)) }
            verify { wishlistActivityRepository.deleteNotifiedBefore(capture(purgeSlot)) }
            assertThat(purgeSlot.captured).isEqualTo(notifiedAtSlot.captured.minus(Duration.ofDays(7)))
        }

        @Test
        fun `should purge even when nothing is ready`() {
            stubPending()
            val purgeSlot = slot<Instant>()
            val before = Instant.now()

            service.flushPendingNotifications()

            verify { wishlistActivityRepository.deleteNotifiedBefore(capture(purgeSlot)) }
            assertThat(purgeSlot.captured).isBetween(before.minus(Duration.ofDays(7)), Instant.now().minus(Duration.ofDays(7)))
        }

        @Test
        fun `should propagate a repository failure`() {
            every { wishlistActivityRepository.findAllPending() } throws RuntimeException("Database error")

            assertThatThrownBy { service.flushPendingNotifications() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Database error")

            verifyNotSent()
        }
    }
}