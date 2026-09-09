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
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.TimeUnit

class BggClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var bggClient: BggClient
    private val testToken = "test-bgg-token-123"

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()
        val webClient = WebClient.builder().baseUrl(baseUrl).build()
        bggClient = BggClient(webClient, testToken)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.close()
    }

    @Nested
    @DisplayName("searchGames")
    inner class SearchGamesTests {

        @Test
        fun `should search games and parse valid XML response`() {
            val xmlResponse = """
                <items>
                    <item id="106437">
                        <name value="SETI: Search for Extraterrestrial Intelligence"/>
                        <yearpublished value="2024"/>
                    </item>
                </items>
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
                    .body(xmlResponse)
                    .build()
            )

            val result = bggClient.searchGames("SETI")

            assertEquals(1, result.items.size)
            assertEquals(106437L, result.items[0].id)
            assertEquals("SETI: Search for Extraterrestrial Intelligence", result.items[0].name?.value)

            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("Bearer $testToken", recordedRequest.headers[HttpHeaders.AUTHORIZATION])
            assertTrue(recordedRequest.target.contains("search"))
            assertTrue(recordedRequest.target.contains("query=SETI"))
        }

        @Test
        fun `should throw BggRequestFailedException on HTTP 500 error`() {
            mockWebServer.enqueue(MockResponse.Builder().code(500).build())

            assertThrows<BggRequestFailedException> {
                bggClient.searchGames("SETI")
            }
        }
    }

    @Nested
    @DisplayName("getGameDetails")
    inner class GetGameDetailsTests {

        @Test
        fun `should fetch single game details and parse XML correctly`() {
            val xmlResponse = """
                <items>
                    <item type="boardgame" id="106437">
                        <name type="primary" value="SETI"/>
                        <minplayers value="1"/>
                        <maxplayers value="4"/>
                    </item>
                </items>
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
                    .body(xmlResponse)
                    .build()
            )

            val result = bggClient.getGameDetails(106437L)

            assertEquals(1, result.items.size)
            assertEquals(106437L, result.items[0].id)

            val recordedRequest = mockWebServer.takeRequest()
            assertTrue(recordedRequest.target.contains("/thing?id=106437&stats=1"))
        }

        @Test
        fun `should throw BggRequestFailedException on connection error`() {
            mockWebServer.enqueue(MockResponse.Builder().code(404).build())

            assertThrows<BggRequestFailedException> {
                bggClient.getGameDetails(999999L)
            }
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
            val xmlResponse = """
                <items>
                    <item type="boardgame" id="1"><name type="primary" value="Game 1"/></item>
                    <item type="boardgame" id="2"><name type="primary" value="Game 2"/></item>
                </items>
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
                    .body(xmlResponse)
                    .build()
            )

            val ids = listOf(1L, 2L)
            val result = bggClient.getGameDetailsBatch(ids)

            assertEquals(2, result.items.size)
            assertEquals(1, mockWebServer.requestCount)

            val recordedRequest = mockWebServer.takeRequest()
            assertTrue(recordedRequest.target.contains("/thing?id=1,2&stats=1"))
        }

        @Test
        fun `should split requests into chunks of 20 when ids count exceeds 20`() {
            val chunk1Xml = """
                <items>
                    ${(1..20).joinToString("\n") { "<item type=\"boardgame\" id=\"$it\"><name type=\"primary\" value=\"Game $it\"/></item>" }}
                </items>
            """.trimIndent()
            val chunk2Xml = """
                <items>
                    ${(21..25).joinToString("\n") { "<item type=\"boardgame\" id=\"$it\"><name type=\"primary\" value=\"Game $it\"/></item>" }}
                </items>
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
                    .body(chunk1Xml)
                    .build()
            )
            mockWebServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
                    .body(chunk2Xml)
                    .build()
            )

            val ids = (1L..25L).toList()
            val result = bggClient.getGameDetailsBatch(ids)

            assertEquals(25, result.items.size)
            assertEquals(2, mockWebServer.requestCount)

            val request1 = mockWebServer.takeRequest()
            val request2 = mockWebServer.takeRequest()

            assertTrue(request1.target.contains("id=" + (1..20).joinToString(",")))
            assertTrue(request2.target.contains("id=" + (21..25).joinToString(",")))
        }

        @Test
        fun `should throw BggRequestFailedException if any batch chunk fails`() {
            mockWebServer.enqueue(MockResponse.Builder().code(500).build())

            assertThrows<BggRequestFailedException> {
                bggClient.getGameDetailsBatch(listOf(100L, 200L))
            }
        }
    }

    @Nested
    @DisplayName("getHotGames")
    inner class GetHotGamesTests {

        @Test
        fun `should fetch hot games list and parse XML correctly`() {
            val xmlResponse = """
                <items>
                    <item id="106437" rank="1">
                        <name value="SETI"/>
                    </item>
                </items>
            """.trimIndent()

            mockWebServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
                    .body(xmlResponse)
                    .build()
            )

            val result = bggClient.getHotGames()

            assertEquals(1, result.items.size)
            assertEquals(106437L, result.items[0].id)

            val recordedRequest = mockWebServer.takeRequest()
            assertTrue(recordedRequest.target.contains("/hot?type=boardgame"))
        }

        @Test
        fun `should throw BggRequestFailedException on failure`() {
            mockWebServer.enqueue(MockResponse.Builder().code(503).build())

            assertThrows<BggRequestFailedException> {
                bggClient.getHotGames()
            }
        }
    }

    @Nested
    @DisplayName("getCardSetsByGame")
    inner class GetCardSetsByGameTests {

        @Test
        fun `should fetch card sets and parse decimal sleeve sizes correctly`() {
            val jsonResponse = """
            {
                "cardSets": [
                    {
                        "cardTypes": [
                            {
                                "name": "Standard Card",
                                "width": "63.5",
                                "height": "88.0",
                                "quantity": "100"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

            mockWebServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(jsonResponse)
                    .build()
            )

            val baseUrl = mockWebServer.url("/").toString()
            val customWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .filter { request, next ->
                    val mockUri = mockWebServer.url(request.url().path + "?" + (request.url().query ?: "")).toUri()
                    val newRequest = org.springframework.web.reactive.function.client.ClientRequest.from(request)
                        .url(mockUri)
                        .build()
                    next.exchange(newRequest)
                }
                .build()

            val localBggClient = BggClient(customWebClient, testToken)

            val result = localBggClient.getCardSetsByGame(106437L)

            assertEquals(1, result.cardSets.size)
            val cardType = result.cardSets[0].cardTypes[0]
            assertEquals("Standard Card", cardType.name)
            assertEquals("63.5", cardType.width)
            assertEquals("88.0", cardType.height)
        }

        @Test
        fun `should return fallback empty response on HTTP error instead of throwing exception`() {
            mockWebServer.enqueue(MockResponse.Builder().code(500).build())

            val baseUrl = mockWebServer.url("/").toString()
            val customWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .filter { request, next ->
                    val mockUri = mockWebServer.url(request.url().path + "?" + (request.url().query ?: "")).toUri()
                    val newRequest = org.springframework.web.reactive.function.client.ClientRequest.from(request)
                        .url(mockUri)
                        .build()
                    next.exchange(newRequest)
                }
                .build()

            val localBggClient = BggClient(customWebClient, testToken)

            val result = localBggClient.getCardSetsByGame(106437L)

            assertNotNull(result)
            assertTrue(result.cardSets.isEmpty())
        }

        @Test
        fun `should return fallback empty response on timeout`() {
            mockWebServer.enqueue(
                MockResponse.Builder()
                    .body("{}")
                    .bodyDelay(12, TimeUnit.SECONDS)
                    .build()
            )

            val baseUrl = mockWebServer.url("/").toString()
            val customWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .filter { request, next ->
                    val mockUri = mockWebServer.url(request.url().path + "?" + (request.url().query ?: "")).toUri()
                    val newRequest = org.springframework.web.reactive.function.client.ClientRequest.from(request)
                        .url(mockUri)
                        .build()
                    next.exchange(newRequest)
                }
                .build()

            val localBggClient = BggClient(customWebClient, testToken)

            val result = localBggClient.getCardSetsByGame(106437L)

            assertNotNull(result)
            assertTrue(result.cardSets.isEmpty())
        }
    }
}