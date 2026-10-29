package com.backend.models.specifications

import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import jakarta.persistence.criteria.JoinType
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object WishlistItemSpecification {

    fun withFilters(wishlistId: UUID, gameName: String?): Specification<WishlistItem> {
        return Specification { root, query, cb ->
            if(query.resultType != Long::class.javaObjectType) {
                root.fetch<WishlistItem, Game>("game", JoinType.INNER)
                root.fetch<WishlistItem, User>("addedBy", JoinType.INNER)
            }

            val predicates = mutableListOf(
                cb.equal(root.get<Wishlist>("wishlist").get<UUID>("id"), wishlistId)
            )

            if(!gameName.isNullOrBlank()) {
                predicates.add(cb.like(cb.lower(root.get<Game>("game").get("name")), "%${gameName.trim().lowercase()}%"))
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}