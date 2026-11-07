package com.backend.unit.utils

import com.backend.models.dtos.BggThingItemXml
import com.backend.models.dtos.BggThingLinkXml
import com.backend.models.dtos.BggThingNameXml
import com.backend.models.dtos.BggThingPollSummaryResultXml
import com.backend.models.dtos.BggThingPollSummaryXml
import com.backend.models.dtos.BggThingRankXml
import com.backend.models.dtos.BggThingRanksXml
import com.backend.models.dtos.BggThingRatingsXml
import com.backend.models.dtos.BggThingStatisticsXml
import com.backend.models.dtos.BggValueXml
import com.backend.models.entities.Game
import com.backend.utils.BggDetails
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

class BggDetailsTest {

    private fun newGame() =
        Game(bggId = 342942L, name = "", lastSyncedAt = Instant.EPOCH)

    private fun value(value: String) =
        BggValueXml(value)

    private fun link(type: String, id: Long, name: String, inbound: Boolean? = null) =
        BggThingLinkXml(type = type, id = id.toString(), value = name, inbound = inbound)

    private fun playerPoll(vararg results: Pair<String, String>) = listOf(
        BggThingPollSummaryXml(
            name = "suggested_numplayers",
            results = results.map { (name, value) -> BggThingPollSummaryResultXml(name = name, value = value) },
        )
    )

    private fun statistics(average: String? = null, weight: String? = null, rank: String? = null) =
        BggThingStatisticsXml(
            ratings = BggThingRatingsXml(
                averageWeight = weight?.let { value(it) },
                average = average?.let { value(it) },
                ranks = rank?.let { BggThingRanksXml(ranks = listOf(BggThingRankXml(name = "boardgame", value = it))) },
            )
        )

    private fun details(
        type: String? = "boardgame",
        names: List<BggThingNameXml> = listOf(BggThingNameXml(type = "primary", value = "Ark Nova")),
        description: String? = null,
        year: String? = null,
        minPlayers: String? = null,
        maxPlayers: String? = null,
        playingTime: String? = null,
        thumbnail: String? = null,
        image: String? = null,
        links: List<BggThingLinkXml> = emptyList(),
        pollSummaries: List<BggThingPollSummaryXml> = emptyList(),
        statistics: BggThingStatisticsXml? = null,
    ) = BggThingItemXml(
        id = 342942L,
        type = type,
        names = names,
        description = description,
        yearPublished = year?.let { value(it) },
        minPlayers = minPlayers?.let { value(it) },
        maxPlayers = maxPlayers?.let { value(it) },
        playingTime = playingTime?.let { value(it) },
        thumbnail = thumbnail,
        image = image,
        links = links,
        pollSummaries = pollSummaries,
        statistics = statistics,
    )

    private fun apply(details: BggThingItemXml, game: Game = newGame()): Game =
        BggDetails.applyBggDetails(game, details)

    @Nested
    @DisplayName("basic fields")
    inner class BasicFieldsTests {

        @Test
        fun `should map the basic fields of a complete game`() {
            val game = apply(
                details(
                    year = "2021",
                    minPlayers = "1",
                    maxPlayers = "4",
                    playingTime = "150",
                    thumbnail = "https://cf.geekdo-images.com/thumb.jpg",
                    image = "https://cf.geekdo-images.com/image.jpg",
                )
            )

            assertEquals("Ark Nova", game.name)
            assertEquals(2021, game.yearPublished)
            assertEquals(1, game.minPlayers)
            assertEquals(4, game.maxPlayers)
            assertEquals(150, game.playingTimeMinutes)
            assertEquals("https://cf.geekdo-images.com/thumb.jpg", game.thumbnailUrl)
            assertEquals("https://cf.geekdo-images.com/image.jpg", game.imageUrl)
        }

        @Test
        fun `should update and return the same game instance`() {
            val original = newGame()

            assertSame(original, apply(details(), original))
        }

        @Test
        fun `should prefer the primary name over alternate names`() {
            val game = apply(details(names = listOf(BggThingNameXml("alternate", "Arche Nova"), BggThingNameXml("primary", "Ark Nova"))))

            assertEquals("Ark Nova", game.name)
        }

        @Test
        fun `should keep the current name when BGG returns no names`() {
            val existing = newGame().apply { name = "Ark Nova" }

            assertEquals("Ark Nova", apply(details(names = emptyList()), existing).name)
        }

        @Test
        fun `should refresh the sync timestamp`() {
            val before = Instant.now()

            val game = apply(details())

            assertFalse(game.lastSyncedAt.isBefore(before))
        }

        @Test
        fun `should overwrite stale values with what BGG returns now`() {
            val existing = newGame().apply {
                imageUrl = "https://old.example/image.jpg"
                playingTimeMinutes = 60
            }

            val game = apply(details(), existing)

            assertNull(game.imageUrl)
            assertNull(game.playingTimeMinutes)
        }

        @ParameterizedTest
        @ValueSource(strings = ["0", "-5", "abc", ""])
        fun `should ignore missing or invalid numeric values`(raw: String) {
            val game = apply(details(year = raw, minPlayers = raw, maxPlayers = raw, playingTime = raw))

            assertNull(game.yearPublished)
            assertNull(game.minPlayers)
            assertNull(game.maxPlayers)
            assertNull(game.playingTimeMinutes)
        }
    }

    @Nested
    @DisplayName("description")
    inner class DescriptionTests {

        @Test
        fun `should keep a null description as null`() {
            assertNull(apply(details(description = null)).description)
        }

        @Test
        fun `should turn line breaks into new lines and strip HTML`() {
            val game = apply(details(description = "Plan a <b>modern</b> zoo.<br/>Build enclosures.<BR>Attract animals.<br />End."))

            assertEquals("Plan a modern zoo.\nBuild enclosures.\nAttract animals.\nEnd.", game.description)
        }

        @Test
        fun `should decode HTML entities`() {
            val game = apply(details(description = "Cats &amp; Dogs&#10;&#10;&quot;Quoted&quot; text &mdash; done"))

            assertEquals("Cats & Dogs\n\n\"Quoted\" text — done", game.description)
        }

        @Test
        fun `should collapse three or more blank lines and trim`() {
            val game = apply(details(description = "\n\nFirst.<br/><br/><br/><br/>Second.\n\n"))

            assertEquals("First.\n\nSecond.", game.description)
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "—description from the publisher",
            "— description from the publisher.",
            "–description from the designer",
            "-description from publisher",
            "—Description from the Artist",
            "—description from the manufacturer",
        ])
        fun `should remove the trailing source credit`(credit: String) {
            val game = apply(details(description = "Build the best zoo.<br/><br/>$credit"))

            assertEquals("Build the best zoo.", game.description)
        }

        @Test
        fun `should keep a source credit that is not at the end`() {
            val game = apply(details(description = "—description from the publisher<br/><br/>Updated rules below."))

            assertTrue(game.description!!.contains("description from the publisher"))
        }
    }

    @Nested
    @DisplayName("player count recommendations")
    inner class PlayerCountTests {

        @ParameterizedTest
        @CsvSource(
            "Best with 2 players, 2",
            "Best with 3–4 players, 3–4",
            "Best with 3-4 players, 3-4",
        )
        fun `should extract the best player count`(raw: String, expected: String) {
            val game = apply(details(pollSummaries = playerPoll("bestwith" to raw)))

            assertEquals(expected, game.bestWith)
        }

        @Test
        fun `should read the recommended count from the misspelled BGG key`() {
            val game = apply(details(pollSummaries = playerPoll("recommmendedwith" to "Recommended with 1–4 players")))

            assertEquals("1–4", game.recommendedWith)
        }

        @ParameterizedTest
        @ValueSource(strings = ["(no votes)", "(Undetermined)", "", "Best with some players"])
        fun `should ignore polls without a usable count`(raw: String) {
            val game = apply(details(pollSummaries = playerPoll("bestwith" to raw, "recommmendedwith" to raw)))

            assertNull(game.bestWith)
            assertNull(game.recommendedWith)
        }

        @Test
        fun `should leave recommendations empty when there is no poll`() {
            val game = apply(details(pollSummaries = emptyList()))

            assertNull(game.bestWith)
            assertNull(game.recommendedWith)
        }
    }

    @Nested
    @DisplayName("expansions")
    inner class ExpansionTests {

        @ParameterizedTest
        @CsvSource(
            "boardgame, false",
            "boardgameexpansion, true",
            "boardgameaccessory, false",
        )
        fun `should flag expansions from the item type`(type: String, expected: Boolean) {
            assertEquals(expected, apply(details(type = type)).isExpansion)
        }

        @Test
        fun `should not flag an item without type as an expansion`() {
            assertEquals(false, apply(details(type = null)).isExpansion)
        }

        @Test
        fun `should store the outgoing expansions of a base game`() {
            val game = apply(
                details(
                    links = listOf(
                        link("boardgameexpansion", 400000L, "Ark Nova: Marine Worlds"),
                        link("boardgameexpansion", 400001L, "Ark Nova: Promo"),
                        link("boardgamemechanic", 2041L, "Open Drafting"),
                    )
                )
            )

            assertEquals(listOf(400000L, 400001L), game.expansionRefs.map { it.bggId })
            assertNull(game.baseGameBggId)
        }

        @Test
        fun `should store the base game of an expansion without listing it as an expansion`() {
            val game = apply(
                details(
                    type = "boardgameexpansion",
                    links = listOf(link("boardgameexpansion", 342942L, "Ark Nova", inbound = true)),
                )
            )

            assertEquals(342942L, game.baseGameBggId)
            assertTrue(game.expansionRefs.isEmpty())
        }
    }

    @Nested
    @DisplayName("statistics")
    inner class StatisticsTests {

        @Test
        fun `should map difficulty, rank and average rating`() {
            val game = apply(details(statistics = statistics(average = "8.53", weight = "3.77", rank = "4")))

            assertEquals(3.77, game.difficulty)
            assertEquals(4, game.bggRank)
            assertEquals(8.53, game.avgRating)
        }

        @Test
        fun `should ignore unranked and unrated games`() {
            val game = apply(details(statistics = statistics(average = "0", weight = "0", rank = "Not Ranked")))

            assertNull(game.difficulty)
            assertNull(game.bggRank)
            assertNull(game.avgRating)
        }

        @Test
        fun `should leave statistics empty when BGG returns none`() {
            val game = apply(details(statistics = null))

            assertNull(game.difficulty)
            assertNull(game.bggRank)
            assertNull(game.avgRating)
        }
    }

    @Nested
    @DisplayName("credits")
    inner class CreditsTests {

        @Test
        fun `should split designers, artists and publishers by link type`() {
            val game = apply(
                details(
                    links = listOf(
                        link("boardgamedesigner", 1L, "Mathias Wigge"),
                        link("boardgameartist", 2L, "Loïc Billiau"),
                        link("boardgameartist", 3L, "Dennis Lohausen"),
                        link("boardgamepublisher", 4L, "Feuerland Spiele"),
                        link("boardgamecategory", 5L, "Animals"),
                    )
                )
            )

            assertEquals(listOf("Mathias Wigge"), game.designers)
            assertEquals(listOf("Loïc Billiau", "Dennis Lohausen"), game.artists)
            assertEquals(listOf("Feuerland Spiele"), game.publishers)
        }

        @Test
        fun `should skip uncredited and unknown entries`() {
            val game = apply(
                details(
                    links = listOf(
                        link("boardgamedesigner", 3L, "(Uncredited)"),
                        link("boardgameartist", 3L, "(Uncredited)"),
                        link("boardgamepublisher", 18291L, "(Unknown)"),
                        link("boardgamepublisher", 18292L, "(Self-Published)"),
                    )
                )
            )

            assertTrue(game.designers.isEmpty())
            assertTrue(game.artists.isEmpty())
            assertEquals(listOf("(Self-Published)"), game.publishers)
        }

        @Test
        fun `should leave credits empty when there are no links`() {
            val game = apply(details(links = emptyList()))

            assertTrue(game.designers.isEmpty())
            assertTrue(game.artists.isEmpty())
            assertTrue(game.publishers.isEmpty())
        }
    }
}