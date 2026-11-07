package com.backend.unit.clients

import com.backend.clients.BggClient
import com.backend.exceptions.BggRequestFailedException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.concurrent.TimeUnit

class BggClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var bggClient: BggClient

    companion object {
        private const val TEST_TOKEN = "test-bgg-token-123"
        private val TEST_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val SHORT_TIMEOUT: Duration = Duration.ofMillis(300)

        private val FULL_THING_XML = """
            <items>
                <item type="boardgame" id="342942">
                    <thumbnail>https://cf.geekdo-images.com/thumb.jpg</thumbnail>
                    <image>https://cf.geekdo-images.com/image.jpg</image>
                    <name type="alternate" value="Arche Nova"/>
                    <name type="primary" value="Ark Nova"/>
                    <description>Plan and build a modern, scientifically managed zoo.</description>
                    <yearpublished value="2021"/>
                    <minplayers value="1"/>
                    <maxplayers value="4"/>
                    <playingtime value="150"/>
                    <minplaytime value="90"/>
                    <maxplaytime value="150"/>
                    <minage value="14"/>
                    <poll-summary name="suggested_numplayers" title="User Suggested Number of Players">
                        <result name="bestwith" value="Best with 2 players"/>
                    </poll-summary>
                    <link type="boardgamecategory" id="1089" value="Animals"/>
                    <link type="boardgamecategory" id="1084" value="Environmental"/>
                    <link type="boardgamemechanic" id="2041" value="Open Drafting"/>
                    <link type="boardgamemechanic" id="2002" value="Tile Placement"/>
                    <link type="boardgamedesigner" id="137506" value="Mathias Wigge"/>
                    <link type="boardgameexpansion" id="400000" value="Ark Nova: Marine Worlds"/>
                    <statistics page="1">
                        <ratings>
                            <usersrated value="60000"/>
                            <average value="8.53"/>
                            <ranks>
                                <rank type="subtype" id="1" name="boardgame" friendlyname="Board Game Rank" value="4" bayesaverage="8.4"/>
                                <rank type="family" id="5497" name="strategygames" friendlyname="Strategy Game Rank" value="3" bayesaverage="8.4"/>
                            </ranks>
                            <averageweight value="3.77"/>
                        </ratings>
                    </statistics>
                </item>
            </items>
        """.trimIndent()
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        bggClient = createClient()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.close()
    }

    private fun createClient(timeout: Duration = TEST_TIMEOUT): BggClient {
        val webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build()
        return BggClient(webClient, TEST_TOKEN, timeout)
    }

    private fun createCardSetsClient(timeout: Duration = TEST_TIMEOUT): BggClient {
        val webClient = WebClient.builder()
            .filter { request, next ->
                val mockUri = mockWebServer.url(request.url().rawPath + "?" + (request.url().rawQuery ?: "")).toUri()
                next.exchange(ClientRequest.from(request).url(mockUri).build())
            }
            .build()

        return BggClient(webClient, TEST_TOKEN, timeout)
    }

    private fun enqueueXml(body: String) {
        mockWebServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
                .body(body)
                .build()
        )
    }

    private fun enqueueJson(body: String) {
        mockWebServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body)
                .build()
        )
    }

    private fun enqueueStatus(code: Int) {
        mockWebServer.enqueue(MockResponse.Builder().code(code).build())
    }

    private fun itemsXml(ids: IntRange): String =
        "<items>" + ids.joinToString("") { "<item type=\"boardgame\" id=\"$it\"><name type=\"primary\" value=\"Game $it\"/></item>" } + "</items>"

    @Nested
    @DisplayName("searchGames")
    inner class SearchGamesTests {

        @Test
        fun `should search games and parse valid XML response`() {
            enqueueXml(
                """
                <items total="1">
                    <item type="boardgame" id="106437">
                        <name type="primary" value="SETI: Search for Extraterrestrial Intelligence"/>
                        <yearpublished value="2024"/>
                    </item>
                </items>
                """.trimIndent()
            )

            val result = bggClient.searchGames("SETI")

            assertEquals(1, result.items.size)
            assertEquals(106437L, result.items[0].id)
            assertEquals("SETI: Search for Extraterrestrial Intelligence", result.items[0].name?.value)
            assertEquals("2024", result.items[0].yearPublished?.value)
        }

        @Test
        fun `should send the bearer token and filter by board games`() {
            enqueueXml("<items/>")

            bggClient.searchGames("SETI")

            val request = mockWebServer.takeRequest()
            assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.AUTHORIZATION])
            assertTrue(request.target.startsWith("/search?"))
            assertTrue(request.target.contains("query=SETI"))
            assertTrue(request.target.contains("type=boardgame"))
        }

        @Test
        fun `should encode spaces in the query`() {
            enqueueXml("<items/>")

            bggClient.searchGames("Ark Nova")

            assertTrue(mockWebServer.takeRequest().target.contains("query=Ark%20Nova"))
        }

        @Test
        fun `should return an empty list when there are no results`() {
            enqueueXml("<items total=\"0\"/>")

            val result = bggClient.searchGames("zzzzzz")

            assertTrue(result.items.isEmpty())
        }

        @Test
        fun `should throw BggRequestFailedException on HTTP 500 error`() {
            enqueueStatus(500)

            assertThrows<BggRequestFailedException> { bggClient.searchGames("SETI") }
        }

        @Test
        fun `should throw BggRequestFailedException on rate limiting`() {
            enqueueStatus(429)

            assertThrows<BggRequestFailedException> { bggClient.searchGames("SETI") }
        }

        @Test
        fun `should throw BggRequestFailedException on an empty body`() {
            enqueueStatus(200)

            assertThrows<BggRequestFailedException> { bggClient.searchGames("SETI") }
        }

        @Test
        fun `should throw BggRequestFailedException on malformed XML`() {
            enqueueXml("<items><item id=\"1\">")

            assertThrows<BggRequestFailedException> { bggClient.searchGames("SETI") }
        }

        @Test
        fun `should throw BggRequestFailedException when BGG does not answer in time`() {
            mockWebServer.enqueue(MockResponse.Builder().body("<items/>").bodyDelay(2, TimeUnit.SECONDS).build())

            assertThrows<BggRequestFailedException> { createClient(SHORT_TIMEOUT).searchGames("SETI") }
        }

        @Test
        fun `should throw BggRequestFailedException when BGG is unreachable`() {
            val unreachableClient = BggClient(WebClient.builder().baseUrl("http://localhost:1").build(), TEST_TOKEN, TEST_TIMEOUT)

            assertThrows<BggRequestFailedException> { unreachableClient.searchGames("SETI") }
        }
    }

    @Nested
    @DisplayName("getGameDetails")
    inner class GetGameDetailsTests {

        @Test
        fun `should request the thing endpoint with stats`() {
            enqueueXml(itemsXml(106437..106437))

            bggClient.getGameDetails(106437L)

            val request = mockWebServer.takeRequest()
            assertTrue(request.target.contains("/thing?id=106437&stats=1"))
            assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.AUTHORIZATION])
        }

        @Test
        fun `should parse every field of a full thing response`() {
            enqueueXml(FULL_THING_XML)

            val item = bggClient.getGameDetails(342942L).items.single()

            assertEquals(342942L, item.id)
            assertEquals("boardgame", item.type)
            assertEquals("Ark Nova", item.primaryName())
            assertEquals("https://cf.geekdo-images.com/thumb.jpg", item.thumbnail)
            assertEquals("https://cf.geekdo-images.com/image.jpg", item.image)
            assertEquals("Plan and build a modern, scientifically managed zoo.", item.description)
            assertEquals("2021", item.yearPublished?.value)
            assertEquals("1", item.minPlayers?.value)
            assertEquals("4", item.maxPlayers?.value)
            assertEquals("150", item.playingTime?.value)
            assertEquals("90", item.minPlayTime?.value)
            assertEquals("14", item.minAge?.value)
            assertEquals("Best with 2 players", item.pollSummaryValue("bestwith"))
            assertEquals(4, item.bggRank())
            assertEquals(8.53, item.averageRating())
            assertEquals("3.77", item.statistics?.ratings?.averageWeight?.value)
        }

        @Test
        fun `should parse mechanics, categories and expansions from the links`() {
            enqueueXml(FULL_THING_XML)

            val item = bggClient.getGameDetails(342942L).items.single()

            assertEquals(listOf(2041L, 2002L), item.mechanics().map { it.bggId })
            assertEquals(listOf("Open Drafting", "Tile Placement"), item.mechanics().map { it.name })
            assertEquals(listOf(1089L, 1084L), item.categories().map { it.bggId })
            assertEquals(listOf(400000L), item.expansionRefs().map { it.bggId })
            assertNull(item.baseGameRef())
        }

        @Test
        fun `should return no items when the game does not exist`() {
            enqueueXml("<items/>")

            assertTrue(bggClient.getGameDetails(999999L).items.isEmpty())
        }

        @Test
        fun `should throw BggRequestFailedException on HTTP 404 error`() {
            enqueueStatus(404)

            assertThrows<BggRequestFailedException> { bggClient.getGameDetails(999999L) }
        }
    }

    @Nested
    @DisplayName("getGameDetailsBatch")
    inner class GetGameDetailsBatchTests {

        @Test
        fun `should return empty list immediately if input bggIds is empty`() {
            val result = bggClient.getGameDetailsBatch(emptyList())

            assertTrue(result.items.isEmpty())
            assertEquals(0, mockWebServer.requestCount)
        }

        @Test
        fun `should fetch single batch when ids count is equal or less than 20`() {
            enqueueXml(itemsXml(1..20))

            val result = bggClient.getGameDetailsBatch((1L..20L).toList())

            assertEquals(20, result.items.size)
            assertEquals(1, mockWebServer.requestCount)
            assertTrue(mockWebServer.takeRequest().target.contains("id=" + (1..20).joinToString(",") + "&stats=1"))
        }

        @Test
        fun `should split requests into chunks of 20 when ids count exceeds 20`() {
            enqueueXml(itemsXml(1..20))
            enqueueXml(itemsXml(21..25))

            val result = bggClient.getGameDetailsBatch((1L..25L).toList())

            assertEquals(25, result.items.size)
            assertEquals(2, mockWebServer.requestCount)
            assertTrue(mockWebServer.takeRequest().target.contains("id=" + (1..20).joinToString(",")))
            assertTrue(mockWebServer.takeRequest().target.contains("id=" + (21..25).joinToString(",")))
        }

        @Test
        fun `should return only the items BGG knows about`() {
            enqueueXml(itemsXml(1..2))

            val result = bggClient.getGameDetailsBatch(listOf(1L, 2L, 999999L))

            assertEquals(listOf(1L, 2L), result.items.map { it.id })
        }

        @Test
        fun `should throw BggRequestFailedException if the first chunk fails`() {
            enqueueStatus(500)

            assertThrows<BggRequestFailedException> { bggClient.getGameDetailsBatch(listOf(100L, 200L)) }
        }

        @Test
        fun `should throw BggRequestFailedException if a later chunk fails`() {
            enqueueXml(itemsXml(1..20))
            enqueueStatus(500)

            assertThrows<BggRequestFailedException> { bggClient.getGameDetailsBatch((1L..25L).toList()) }
            assertEquals(2, mockWebServer.requestCount)
        }
    }

    @Nested
    @DisplayName("getHotGames")
    inner class GetHotGamesTests {

        @Test
        fun `should fetch hot games list and parse XML correctly`() {
            enqueueXml(
                """
                <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
                    <item id="106437" rank="1">
                        <thumbnail value="https://cf.geekdo-images.com/seti.jpg"/>
                        <name value="SETI"/>
                        <yearpublished value="2024"/>
                    </item>
                    <item id="342942" rank="2">
                        <name value="Ark Nova"/>
                    </item>
                </items>
                """.trimIndent()
            )

            val result = bggClient.getHotGames()

            assertEquals(listOf(106437L, 342942L), result.items.map { it.id })
            assertEquals(listOf(1, 2), result.items.map { it.rank })
            assertEquals("SETI", result.items[0].name?.value)
            assertEquals("https://cf.geekdo-images.com/seti.jpg", result.items[0].thumbnail?.value)
            assertEquals("2024", result.items[0].yearPublished?.value)
            assertNull(result.items[1].thumbnail)
        }

        @Test
        fun `should request the board game hot list`() {
            enqueueXml("<items/>")

            bggClient.getHotGames()

            assertTrue(mockWebServer.takeRequest().target.contains("/hot?type=boardgame"))
        }

        @Test
        fun `should throw BggRequestFailedException on failure`() {
            enqueueStatus(503)

            assertThrows<BggRequestFailedException> { bggClient.getHotGames() }
        }
    }

    @Nested
    @DisplayName("getCardSetsByGame")
    inner class GetCardSetsByGameTests {

        @Test
        fun `should fetch card sets and parse decimal sleeve sizes correctly`() {
            enqueueJson(
                """
                {
                    "cardSets": [
                        {
                            "cardTypes": [
                                { "name": "Standard Card", "width": "63.5", "height": "88.0", "quantity": "100", "quantity_note": "incl. 2 spare" },
                                { "name": "Mini Card", "width": "44", "height": "68", "quantity": "40" }
                            ]
                        }
                    ],
                    "extra": "ignored"
                }
                """.trimIndent()
            )

            val result = createCardSetsClient().getCardSetsByGame(106437L)

            val cardTypes = result.cardSets.single().cardTypes
            assertEquals(2, cardTypes.size)
            assertEquals("Standard Card", cardTypes[0].name)
            assertEquals("63.5", cardTypes[0].width)
            assertEquals("88.0", cardTypes[0].height)
            assertEquals("100", cardTypes[0].quantity)
            assertEquals("incl. 2 spare", cardTypes[0].quantityNote)
            assertNull(cardTypes[1].quantityNote)
        }

        @Test
        fun `should call the geekdo endpoint without the BGG token`() {
            enqueueJson("{\"cardSets\":[]}")

            createCardSetsClient().getCardSetsByGame(106437L)

            val request = mockWebServer.takeRequest()
            assertTrue(request.target.startsWith("/api/cardsetsbygame?"))
            assertTrue(request.target.contains("objectid=106437"))
            assertTrue(request.target.contains("nosession=1"))
            assertNull(request.headers[HttpHeaders.AUTHORIZATION])
        }

        @Test
        fun `should return an empty response when the game has no card sets`() {
            enqueueJson("{}")

            assertTrue(createCardSetsClient().getCardSetsByGame(106437L).cardSets.isEmpty())
        }

        @Test
        fun `should return fallback empty response on HTTP error instead of throwing exception`() {
            enqueueStatus(500)

            assertTrue(createCardSetsClient().getCardSetsByGame(106437L).cardSets.isEmpty())
        }

        @Test
        fun `should return fallback empty response on an empty body`() {
            enqueueStatus(200)

            assertTrue(createCardSetsClient().getCardSetsByGame(106437L).cardSets.isEmpty())
        }

        @Test
        fun `should return fallback empty response on malformed JSON`() {
            enqueueJson("{\"cardSets\": [")

            assertTrue(createCardSetsClient().getCardSetsByGame(106437L).cardSets.isEmpty())
        }

        @Test
        fun `should return fallback empty response on timeout`() {
            mockWebServer.enqueue(
                MockResponse.Builder()
                    .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body("{\"cardSets\":[{\"cardTypes\":[{\"name\":\"Late\"}]}]}")
                    .bodyDelay(2, TimeUnit.SECONDS)
                    .build()
            )

            val result = createCardSetsClient(SHORT_TIMEOUT).getCardSetsByGame(106437L)

            assertTrue(result.cardSets.isEmpty())
        }
    }
}