package com.backend.utils

import com.backend.models.dtos.BggThingItemXml
import com.backend.models.entities.Game
import org.jsoup.Jsoup
import java.time.Instant

object BggDetails {

    fun applyBggDetails(game: Game, details: BggThingItemXml): Game {
        val cleanDescription = details.description
            ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            ?.let { Jsoup.parse(it).body().wholeText() }
            ?.replace(Regex("""[—–-]\s*description from (the )?(publisher|designer|artist|manufacturer)\.?\s*$""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("\n{3,}"), "\n\n")
            ?.trim()

        game.name = details.primaryName() ?: game.name
        game.yearPublished = details.yearPublished?.value?.toIntOrNull()
        game.thumbnailUrl = details.thumbnail
        game.imageUrl = details.image
        game.minPlayers = details.minPlayers?.value?.toIntOrNull()?.takeIf { it > 0 }
        game.maxPlayers = details.maxPlayers?.value?.toIntOrNull()?.takeIf { it > 0 }
        game.playingTimeMinutes = details.playingTime?.value?.toIntOrNull()?.takeIf { it > 0 }
        game.description = cleanDescription
        game.bestWith = parsePlayerCountRecommendation(details.pollSummaryValue("bestwith"))
        game.recommendedWith = parsePlayerCountRecommendation(details.pollSummaryValue("recommmendedwith"))
        game.expansionRefs = details.expansionRefs()
        game.lastSyncedAt = Instant.now()
        game.isExpansion = details.type != "boardgame"

        val baseGameRef = details.baseGameRef()
        game.baseGameBggId = baseGameRef?.bggId

        game.difficulty = details.statistics
            ?.ratings
            ?.averageWeight
            ?.value
            ?.toDoubleOrNull()
            ?.takeIf { it > 0 }

        game.bggRank = details.bggRank()
        game.avgRating = details.averageRating()

        game.designers = details.links.filter { it.type == "boardgamedesigner" }.map { it.value }
        game.artists = details.links.filter { it.type == "boardgameartist" }.map { it.value }
        game.publishers = details.links.filter { it.type == "boardgamepublisher" }.map { it.value }

        return game
    }

    private val PLAYER_COUNT_REGEX = Regex("""(\d+(?:[–-]\d+)?)""")

    private fun parsePlayerCountRecommendation(raw: String?): String? {
        if(raw.isNullOrBlank()) return null
        if(raw == "(no votes)" || raw == "(Undetermined)") return null

        return PLAYER_COUNT_REGEX.find(raw)?.value
    }
}