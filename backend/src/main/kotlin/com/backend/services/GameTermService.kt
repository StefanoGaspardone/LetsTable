package com.backend.services

import com.backend.models.dtos.BggTermRef
import com.backend.models.dtos.BggThingItemXml
import com.backend.models.dtos.GameTermDTO
import com.backend.models.entities.Category
import com.backend.models.entities.Mechanic
import com.backend.repositories.CategoryRepository
import com.backend.repositories.MechanicRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

data class GameTerms(
    val mechanics: Map<Long, Mechanic> = emptyMap(),
    val categories: Map<Long, Category> = emptyMap(),
)

@Service
class GameTermService(
    private val mechanicRepository: MechanicRepository,
    private val categoryRepository: CategoryRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun resolve(details: Collection<BggThingItemXml>): GameTerms {
        val mechanicRefs = details.flatMap { it.mechanics() }.distinctBy { it.bggId }
        val categoryRefs = details.flatMap { it.categories() }.distinctBy { it.bggId }

        logger.debug("\n\t[DEBUG] [game_term_service][resolve] Resolving terms\n\tmechanics={}\n\tcategories={}", mechanicRefs.size, categoryRefs.size)

        return GameTerms(
            mechanics = resolveMechanics(mechanicRefs),
            categories = resolveCategories(categoryRefs),
        )
    }

    fun getMechanics(): List<GameTermDTO> {
        logger.debug("\n\t[DEBUG] [game_term_service][get_mechanics] Retrieving mechanics")

        try {
            val mechanics = mechanicRepository.findAll()
                .map { GameTermDTO.from(it) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            logger.info("\n\t[INFO] [game_term_service][get_mechanics] Retrieved {} mechanics", mechanics.size)
            return mechanics
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_term_service][get_mechanics] Error retrieving mechanics: {}", e.message, e)
            throw e
        }
    }

    fun getCategories(): List<GameTermDTO> {
        logger.debug("\n\t[DEBUG] [game_term_service][get_categories] Retrieving categories")

        try {
            val categories = categoryRepository.findAll()
                .map { GameTermDTO.from(it) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            logger.info("\n\t[INFO] [game_term_service][get_categories] Retrieved {} categories", categories.size)
            return categories
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_term_service][get_categories] Error retrieving categories: {}", e.message, e)
            throw e
        }
    }

    private fun resolveMechanics(refs: List<BggTermRef>): Map<Long, Mechanic> {
        if(refs.isEmpty()) {
            return emptyMap()
        }

        val bggIds = refs.map { it.bggId }
        val existing = mechanicRepository.findAllByBggIdIn(bggIds).associateBy { it.bggId }

        val toSave = refs.mapNotNull { ref ->
            val current = existing[ref.bggId]

            when {
                current == null -> Mechanic(bggId = ref.bggId, name = ref.name)
                current.name != ref.name -> current.apply { name = ref.name }
                else -> null
            }
        }

        if(toSave.isEmpty()) {
            return existing
        }

        return try {
            val saved = mechanicRepository.saveAll(toSave)

            logger.info("\n\t[INFO] [game_term_service][resolve_mechanics] Saved {} new or renamed mechanics", saved.size)
            existing + saved.associateBy { it.bggId }
        } catch(e: DataIntegrityViolationException) {
            logger.warn("\n\t[WARN] [game_term_service][resolve_mechanics] Concurrent insert detected, reloading mechanics\n\treason={}", e.message)
            mechanicRepository.findAllByBggIdIn(bggIds).associateBy { it.bggId }
        }
    }

    private fun resolveCategories(refs: List<BggTermRef>): Map<Long, Category> {
        if(refs.isEmpty()) {
            return emptyMap()
        }

        val bggIds = refs.map { it.bggId }
        val existing = categoryRepository.findAllByBggIdIn(bggIds).associateBy { it.bggId }

        val toSave = refs.mapNotNull { ref ->
            val current = existing[ref.bggId]

            when {
                current == null -> Category(bggId = ref.bggId, name = ref.name)
                current.name != ref.name -> current.apply { name = ref.name }
                else -> null
            }
        }

        if(toSave.isEmpty()) {
            return existing
        }

        return try {
            val saved = categoryRepository.saveAll(toSave)

            logger.info("\n\t[INFO] [game_term_service][resolve_categories] Saved {} new or renamed categories", saved.size)
            existing + saved.associateBy { it.bggId }
        } catch(e: DataIntegrityViolationException) {
            logger.warn("\n\t[WARN] [game_term_service][resolve_categories] Concurrent insert detected, reloading categories\n\treason={}", e.message)
            categoryRepository.findAllByBggIdIn(bggIds).associateBy { it.bggId }
        }
    }
}