package com.backend.seeders

import com.backend.models.entities.FriendRequest
import com.backend.models.entities.User
import com.backend.models.enums.FriendRequestStatus
import com.backend.repositories.FriendRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FriendSeeder(
    private val friendRequestRepository: FriendRequestRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun seed(users: List<User>) {
        logger.debug("\n\t[DEBUG] [friend_seeder][seed] Seeding demo friend requests")

        try {
            val (marco, anna, luca, elena) = users

            createIfNotExists(sender = anna, receiver = marco, status = FriendRequestStatus.PENDING)
            createIfNotExists(sender = anna, receiver = luca, status = FriendRequestStatus.ACCEPTED)
            createIfNotExists(sender = elena, receiver = luca, status = FriendRequestStatus.PENDING)
            createIfNotExists(sender = marco, receiver = luca, status = FriendRequestStatus.ACCEPTED)

            logger.info("\n\t[INFO] [friend_seeder][seed] Demo friend requests ready")
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [friend_seeder][seed] Error seeding friend requests: {}", e.message)
            throw e
        }
    }

    private fun createIfNotExists(sender: User, receiver: User, status: FriendRequestStatus) {
        val existing = friendRequestRepository.findBySenderIdAndReceiverId(sender.id!!, receiver.id!!)
        if(existing.isPresent) return

        val reciprocal = friendRequestRepository.findBySenderIdAndReceiverId(receiver.id!!, sender.id!!)
        if(reciprocal.isPresent) return

        friendRequestRepository.save(
            FriendRequest(
                sender = sender,
                receiver = receiver,
                status = status,
            )
        )
    }
}