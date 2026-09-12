package com.backend.models.specifications

import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

object UserSpecification {

    fun withFilters(role: UserRole?, status: AccountStatus?, search: String?): Specification<User> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            role?.let { predicates.add(cb.equal(root.get<UserRole>("role"), it)) }
            status?.let { predicates.add(cb.equal(root.get<AccountStatus>("accountStatus"), it)) }

            search?.takeIf { it.isNotBlank() }?.let {
                val pattern = "%${it.trim().lowercase()}%"
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                    )
                )
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}