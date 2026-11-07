package com.backend.repositories

import com.backend.models.entities.PushToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

@Repository
interface PushTokenRepository: JpaRepository<PushToken, UUID> {

    fun findByToken(token: String): Optional<PushToken>

    fun deleteByTokenAndUserId(token: String, userId: UUID)

    @Modifying
    @Query("DELETE FROM PushToken pt WHERE pt.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID)

    @Query(
        """
        SELECT pt FROM PushToken pt
        WHERE pt.user.id IN :userIds
        AND pt.user.notificationsEnabled = true
        AND pt.user.accountStatus = 'ACTIVE'
        """
    )
    fun findAllForNotifiableUsers(@Param("userIds") userIds: Collection<UUID>): List<PushToken>

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("DELETE FROM PushToken pt WHERE pt.token IN :tokens")
    fun deleteAllByTokenIn(@Param("tokens") tokens: Collection<String>)
}