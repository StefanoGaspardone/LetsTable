package com.backend.repositories

import com.backend.models.entities.Mechanic
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MechanicRepository: JpaRepository<Mechanic, UUID> {
    fun findAllByBggIdIn(bggIds: Collection<Long>): List<Mechanic>
}