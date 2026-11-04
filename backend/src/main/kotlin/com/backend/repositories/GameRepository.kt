package com.backend.repositories

import com.backend.models.entities.Game
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface GameRepository: JpaRepository<Game, UUID>, JpaSpecificationExecutor<Game> {

    fun findByBggId(bggId: Long): Optional<Game>

    fun findAllByRankIsNotNullOrderByRankAsc(pageable: Pageable): Page<Game>

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Game g SET g.rank = null")
    fun clearAllRanks()

    fun findAllByBggIdIn(bggIds: Collection<Long>): List<Game>

    @Query(
        """
        SELECT g FROM Game g
        WHERE LOWER(g.name) LIKE :namePattern ESCAPE '\'
          AND (:isExpansion IS NULL OR COALESCE(g.isExpansion, false) = :isExpansion)
          AND (:mechanicId IS NULL OR EXISTS (SELECT 1 FROM Game g2 JOIN g2.mechanics m WHERE g2 = g AND m.id = :mechanicId))
          AND (:categoryId IS NULL OR EXISTS (SELECT 1 FROM Game g3 JOIN g3.categories c WHERE g3 = g AND c.id = :categoryId))
        """
    )
    fun searchCatalog(
        @Param("namePattern") namePattern: String,
        @Param("isExpansion") isExpansion: Boolean?,
        @Param("mechanicId") mechanicId: UUID?,
        @Param("categoryId") categoryId: UUID?,
        pageable: Pageable,
    ): Page<Game>
}