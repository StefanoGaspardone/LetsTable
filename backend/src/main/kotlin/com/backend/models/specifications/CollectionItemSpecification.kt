package com.backend.models.specifications

import com.backend.models.entities.CollectionItem
import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.User
import jakarta.persistence.criteria.JoinType
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object CollectionItemSpecification {

    fun withFilters(userId: UUID, gameName: String?, played: Boolean?, isExpansion: Boolean?): Specification<CollectionItem> {
        return Specification { root, query, cb ->
            if(query.resultType != Long::class.javaObjectType) {
                root.fetch<CollectionItem, Game>("game", JoinType.INNER)
            }

            val game = root.get<Game>("game")
            val predicates = mutableListOf(
                cb.equal(root.get<User>("user").get<UUID>("id"), userId)
            )

            if(!gameName.isNullOrBlank()) {
                predicates.add(cb.like(cb.lower(game.get("name")), "%${gameName.trim().lowercase()}%"))
            }

            if(isExpansion != null) {
                predicates.add(cb.equal(game.get<Boolean>("isExpansion"), isExpansion))
            }

            if(played != null) {
                val createdBySubquery = query.subquery(Long::class.java)
                val createdByRoot = createdBySubquery.from(Match::class.java)

                createdBySubquery.select(cb.literal(1L))
                createdBySubquery.where(
                    cb.equal(createdByRoot.get<Game>("game"), game),
                    cb.equal(createdByRoot.get<User>("createdBy").get<UUID>("id"), userId),
                )

                val playerSubquery = query.subquery(Long::class.java)
                val playerRoot = playerSubquery.from(MatchPlayer::class.java)

                playerSubquery.select(cb.literal(1L))
                playerSubquery.where(
                    cb.equal(playerRoot.get<Match>("match").get<Game>("game"), game),
                    cb.equal(playerRoot.get<User>("user").get<UUID>("id"), userId),
                )

                val hasBeenPlayed = cb.or(cb.exists(createdBySubquery), cb.exists(playerSubquery))
                predicates.add(if(played) hasBeenPlayed else cb.not(hasBeenPlayed))
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}