package com.backend.models.specifications

import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.User
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

object MatchSpecification {

    fun withFilters(userId: UUID, gameId: UUID?, fromDate: LocalDate?, toDate: LocalDate?, onlyCompleted: Boolean = false): Specification<Match> {
        return Specification { root, query, cb ->
            if(query.resultType != Long::class.javaObjectType) {
                root.fetch<Match, Game>("game", JoinType.INNER)
                root.fetch<Match, User>("createdBy", JoinType.INNER)
            }

            val predicates = mutableListOf<Predicate>()

            val playerSubquery = query.subquery(Long::class.java)
            val playerRoot = playerSubquery.from(MatchPlayer::class.java)
            playerSubquery.select(cb.literal(1L))
            playerSubquery.where(
                cb.equal(playerRoot.get<Match>("match"), root),
                cb.equal(playerRoot.get<User>("user").get<UUID>("id"), userId),
            )

            predicates.add(cb.or(cb.equal(root.get<User>("createdBy").get<UUID>("id"), userId), cb.exists(playerSubquery)))

            if(gameId != null) {
                predicates.add(cb.equal(root.get<Game>("game").get<UUID>("id"), gameId))
            }

            if(fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("playedAt"), fromDate.atStartOfDay(ZoneOffset.UTC).toInstant()))
            }

            if(toDate != null) {
                predicates.add(cb.lessThan(root.get("playedAt"), toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
            }

            if(onlyCompleted) {
                predicates.add(cb.isNotNull(root.get<Int>("durationMinutes")))
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}