package com.backend.repositories

import com.backend.models.entities.BggRankIndex
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BggRankIndexRepository: JpaRepository<BggRankIndex, Long> {

    fun findAllByOrderByRankAsc(pageable: Pageable): Page<BggRankIndex>
}