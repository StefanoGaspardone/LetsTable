package com.backend.repositories

import com.backend.models.entities.Category
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CategoryRepository: JpaRepository<Category, UUID> {
    fun findAllByBggIdIn(bggIds: Collection<Long>): List<Category>
}