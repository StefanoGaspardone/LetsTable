package com.backend.utils

import com.backend.models.dtos.BggThingItemXml
import com.backend.models.entities.Game
import org.jsoup.Jsoup
import java.time.Instant

object BggDetails {

    private val PLAYER_COUNT_REGEX = Regex("""(\d+(?:[–-]\d+)?)""")
    private val UNKNOWN_CREDITS = setOf("(Uncredited)", "(Unknown)")

    fun applyBggDetails(game: Game, details: BggThingItemXml): Game {
        val cleanDescription = details.description
            ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            ?.let { Jsoup.parse(it).body().wholeText() }
            ?.replace(Regex("""[—–-]\s*description from (the )?(publisher|designer|artist|manufacturer)\.?\s*$""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("\n{3,}"), "\n\n")
            ?.trim()

        game.name = details.primaryName() ?: game.name
        game.yearPublished = details.yearPublished?.value?.toIntOrNull()?.takeIf { it > 0 }
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
        game.isExpansion = details.type == "boardgameexpansion"

        val baseGameRef = details.baseGameRef()
        game.baseGameBggId = baseGameRef?.bggId

        game.difficulty = details.statistics
            ?.ratings
            ?.averageWeight
            ?.value
            ?.toDoubleOrNull()
            ?.takeIf { it > 0 }

        game.bggRank = details.bggRank()
        game.avgRating = details.averageRating()?.takeIf { it > 0 }

        game.designers = credits(details, "boardgamedesigner")
        game.artists = credits(details, "boardgameartist")
        game.publishers = credits(details, "boardgamepublisher")

        return game
    }

    private fun credits(details: BggThingItemXml, type: String): List<String> =
        details.links
            .filter { it.type == type && it.value !in UNKNOWN_CREDITS }
            .map { it.value }

    private fun parsePlayerCountRecommendation(raw: String?): String? {
        if(raw.isNullOrBlank()) return null
        if(raw == "(no votes)" || raw == "(Undetermined)") return null

        return PLAYER_COUNT_REGEX.find(raw)?.value
    }
}