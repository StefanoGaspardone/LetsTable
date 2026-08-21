package com.backend.models.specifications

import com.backend.models.entities.Game
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object WishlistItemSpecification {
    fun withFilters(wishlistId: UUID, gameName: String?): Specification<WishlistItem> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf(
                cb.equal(root.get<Wishlist>("wishlist").get<UUID>("id"), wishlistId)
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