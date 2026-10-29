package com.backend.models.specifications

import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistMember
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

enum class WishlistFilterType {
    ALL, SHARED, PRIVATE
}

object WishlistSpecification {

    fun withFilters(userId: UUID, type: WishlistFilterType?): Specification<Wishlist> {
        return Specification { root, query, cb ->
            if(query.resultType != Long::class.javaObjectType) {
                root.fetch<Wishlist, User>("owner", JoinType.INNER)
            }

            val predicates = mutableListOf<Predicate>()

            val isOwner = cb.equal(root.get<User>("owner").get<UUID>("id"), userId)

            val memberSubquery = query.subquery(Long::class.java)
            val memberRoot = memberSubquery.from(WishlistMember::class.java)
            memberSubquery.select(cb.literal(1L))
            memberSubquery.where(
                cb.equal(memberRoot.get<Wishlist>("wishlist"), root),
                cb.equal(memberRoot.get<User>("user").get<UUID>("id"), userId),
            )

            predicates.add(cb.or(isOwner, cb.exists(memberSubquery)))

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