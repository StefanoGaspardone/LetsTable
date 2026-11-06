package com.backend.repositories

import com.backend.models.entities.WishlistActivity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface WishlistActivityRepository: JpaRepository<WishlistActivity, UUID> {

    @Query(
        """
        SELECT a FROM WishlistActivity a
        JOIN FETCH a.wishlist
        JOIN FETCH a.actor
        JOIN FETCH a.game
        WHERE a.notifiedAt IS NULL
        ORDER BY a.createdAt ASC
        """
    )
    fun findAllPending(): List<WishlistActivity>

    @Modifying
    @Query("UPDATE WishlistActivity a SET a.notifiedAt = :now WHERE a.id IN :ids")
    fun markNotified(@Param("ids") ids: Collection<UUID>, @Param("now") now: Instant): Int

    @Modifying
    @Query("DELETE FROM WishlistActivity a WHERE a.notifiedAt < :cutoff")
    fun deleteNotifiedBefore(@Param("cutoff") cutoff: Instant): Int

    @Modifying
    @Query("DELETE FROM WishlistActivity a WHERE a.actor.id = :actorId")
    fun deleteAllByActorId(@Param("actorId") actorId: UUID): Int
}