package com.backend.models.specifications

import com.backend.models.entities.Game
import org.springframework.data.jpa.domain.Specification

object GameSpecification {
    fun withFilters(search: String?, isExpansion: Boolean?): Specification<Game> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            if(!search.isNullOrBlank()) {
                predicates.add(
                    cb.like(cb.lower(root.get("name")), "%${search.trim().lowercase()}%")
                )
            }

            if(isExpansion != null) {
                predicates.add(
                    cb.equal(root.get<Boolean>("isExpansion"), isExpansion)
                )
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}