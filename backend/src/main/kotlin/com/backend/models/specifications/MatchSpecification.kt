package com.backend.models.specifications

import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.User
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.util.UUID

object MatchSpecification {
    fun withFilters(userId: UUID, gameId: UUID?, fromDate: LocalDate?, toDate: LocalDate?): Specification<Match> {
        return Specification { root, query, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            val creatorPredicate = cb.equal(root.get<User>("createdBy").get<UUID>("id"), userId)

            val subquery = query.subquery(Long::class.java)
            val mpRoot = subquery.from(MatchPlayer::class.java)
            subquery.select(cb.literal(1L))
            subquery.where(
                cb.equal(mpRoot.get<Match>("match"), root),
                cb.equal(mpRoot.get<User>("user").get<UUID>("id"), userId),
            )
            val playerPredicate = cb.exists(subquery)

            predicates.add(cb.or(creatorPredicate, playerPredicate))

            if(gameId != null) {
                predicates.add(cb.equal(root.get<com.backend.models.entities.Game>("game").get<UUID>("id"), gameId))
            }

            if(fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("playedAt"), fromDate))
            }

            if(toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("playedAt"), toDate))
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}