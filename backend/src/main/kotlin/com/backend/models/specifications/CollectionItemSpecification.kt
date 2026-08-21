package com.backend.models.specifications

import com.backend.models.entities.CollectionItem
import com.backend.models.entities.Game
import com.backend.models.entities.User
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object CollectionItemSpecification {
    fun withFilters(userId: UUID, gameName: String?): Specification<CollectionItem> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf(
                cb.equal(root.get<User>("user").get<UUID>("id"), userId)
            )

            if(!gameName.isNullOrBlank()) {
                predicates.add(
                    cb.like(cb.lower(root.get<Game>("game").get("name")), "%${gameName.lowercase()}%")
                )
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}