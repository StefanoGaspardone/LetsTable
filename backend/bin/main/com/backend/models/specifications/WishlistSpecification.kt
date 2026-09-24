package com.backend.models.specifications

import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistMember
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

enum class WishlistFilterType {
    ALL, SHARED, PRIVATE
}

object WishlistSpecification {
    fun withFilters(userId: UUID, type: WishlistFilterType?): Specification<Wishlist> {
        return Specification { root, query, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            val isOwner = cb.equal(root.get<User>("owner").get<UUID>("id"), userId)

            val memberSubquery = query.subquery(Long::class.java)
            val memberRoot = memberSubquery.from(WishlistMember::class.java)
            memberSubquery.select(cb.literal(1L))
            memberSubquery.where(
                cb.equal(memberRoot.get<Wishlist>("wishlist"), root),
                cb.equal(memberRoot.get<User>("user").get<UUID>("id"), userId),
            )
            val isMember = cb.exists(memberSubquery)

            val isDefault = cb.isTrue(root.get("isDefault"))

            predicates.add(cb.or(isOwner, isMember, isDefault))

            when(type) {
                WishlistFilterType.SHARED -> predicates.add(cb.isTrue(root.get("isShared")))
                WishlistFilterType.PRIVATE -> {
                    predicates.add(cb.isFalse(root.get("isShared")))
                    predicates.add(cb.isFalse(root.get("isDefault")))
                }
                else -> {}
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}