package com.backend.repositories

import com.backend.models.entities.FriendRequest
import com.backend.models.enums.FriendRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface FriendRequestRepository: JpaRepository<FriendRequest, UUID> {

    fun findBySenderIdAndReceiverId(senderId: UUID, receiverId: UUID): Optional<FriendRequest>

    @Query(
        """
        SELECT fr FROM FriendRequest fr
        JOIN FETCH fr.sender
        JOIN FETCH fr.receiver
        WHERE fr.receiver.id = :receiverId AND fr.status = :status
        """
    )
    fun findByReceiverIdAndStatus(@Param("receiverId") receiverId: UUID, @Param("status") status: FriendRequestStatus): List<FriendRequest>

    @Query(
        """
        SELECT fr FROM FriendRequest fr
        JOIN FETCH fr.sender
        JOIN FETCH fr.receiver
        WHERE fr.sender.id = :senderId AND fr.status = :status
        """
    )
    fun findBySenderIdAndStatus(@Param("senderId") senderId: UUID, @Param("status") status: FriendRequestStatus): List<FriendRequest>

    @Query(
        """
        SELECT fr FROM FriendRequest fr
        JOIN FETCH fr.sender
        JOIN FETCH fr.receiver
        WHERE fr.status = 'ACCEPTED'
        AND (fr.sender.id = :userId OR fr.receiver.id = :userId)
        """
    )
    fun findAllFriendshipsForUser(@Param("userId") userId: UUID): List<FriendRequest>

    @Query(
        """
        SELECT fr FROM FriendRequest fr
        WHERE fr.status = 'ACCEPTED'
        AND ((fr.sender.id = :userIdA AND fr.receiver.id = :userIdB)
             OR (fr.sender.id = :userIdB AND fr.receiver.id = :userIdA))
        """
    )
    fun findFriendshipBetween(@Param("userIdA") userIdA: UUID, @Param("userIdB") userIdB: UUID): Optional<FriendRequest>
}