package com.backend.integration.controllers

import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FileOwnerType
import com.backend.models.enums.UserRole
import com.backend.repositories.UploadedFileRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import com.backend.services.StorageService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*

@AutoConfigureMockMvc
class GameRuleControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var uploadedFileRepository: UploadedFileRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var storageService: StorageService

    private val storedObjects = mutableMapOf<String, ByteArray>()

    @BeforeEach
    fun setUpStorageMock() {
        storedObjects.clear()

        doAnswer {
            val objectKey = it.arguments[0] as String
            val inputStream = it.arguments[1] as InputStream

            storedObjects[objectKey] = inputStream.readBytes()
        }.whenever(storageService).putObject(
            any(),
            any(),
            any(),
            any()
        )

        whenever(storageService.getObject(any()))
            .thenAnswer {
                val objectKey = it.arguments[0] as String
                val content = storedObjects[objectKey]
                    ?: error("Object not found in test storage: $objectKey")

                ByteArrayInputStream(content)
            }

        doAnswer {
            val objectKey = it.arguments[0] as String
            storedObjects.remove(objectKey)
            Unit
        }.whenever(storageService).deleteObject(any())
    }

    private fun persistUser(username: String = "stefano"): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = UserRole.USER,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun pdfFile(
        fileName: String = "Rulebook.pdf",
        content: ByteArray = "fake pdf content".toByteArray()
    ) =
        MockMultipartFile(
            "file",
            fileName,
            "application/pdf",
            content
        )

    private fun uploadPdf(
        gameId: UUID,
        user: User,
        fileName: String = "Rulebook.pdf"
    ): String {
        val result = mockMvc.perform(
            multipart("/api/v1/games/$gameId/rules")
                .file(pdfFile(fileName = fileName))
                .header(
                    HttpHeaders.AUTHORIZATION,
                    authHeader(user)
                )
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectIdFromResponse(
            result.response.contentAsString
        )
    }

    private fun objectIdFromResponse(json: String): String {
        val regex =
            """"id":"([a-f0-9\-]{36})"""".toRegex()

        return regex.find(json)
            ?.groupValues
            ?.get(1)
            ?: error(
                "Could not extract id from response: $json"
            )
    }

    @AfterEach
    fun cleanUp() {
        storedObjects.clear()
        uploadedFileRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/games/{gameId}/rules
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/games/{gameId}/rules")
    inner class UploadRuleFileTests {

        @Test
        fun `should upload a PDF file and persist metadata`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()

            mockMvc.perform(
                multipart("/api/v1/games/$gameId/rules")
                    .file(
                        pdfFile(
                            fileName = "Dune-Imperium-Rulebook.pdf"
                        )
                    )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.fileName")
                        .value("Dune-Imperium-Rulebook.pdf")
                )
                .andExpect(
                    jsonPath("$.uploadedByUsername")
                        .value("stefano")
                )

            val files =
                uploadedFileRepository
                    .findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(
                        FileOwnerType.GAME_RULE,
                        gameId
                    )

            assertThat(files).hasSize(1)

            assertThat(files[0].fileName)
                .isEqualTo("Dune-Imperium-Rulebook.pdf")

            assertThat(storedObjects)
                .hasSize(1)
        }

        @Test
        fun `should return 400 when file type is not a PDF`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()

            val imageFile = MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                "fake image content".toByteArray()
            )

            mockMvc.perform(
                multipart("/api/v1/games/$gameId/rules")
                    .file(imageFile)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isBadRequest)

            assertThat(
                uploadedFileRepository
                    .findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(
                        FileOwnerType.GAME_RULE,
                        gameId
                    )
            ).isEmpty()

            assertThat(storedObjects).isEmpty()
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val gameId = UUID.randomUUID()

            mockMvc.perform(
                multipart("/api/v1/games/$gameId/rules")
                    .file(pdfFile())
            )
                .andExpect(status().isForbidden)

            assertThat(storedObjects).isEmpty()
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/games/{gameId}/rules
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/games/{gameId}/rules")
    inner class ListRuleFilesTests {

        @Test
        fun `should list uploaded files for a game`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()

            uploadPdf(
                gameId,
                user,
                fileName = "Rulebook-1.pdf"
            )

            uploadPdf(
                gameId,
                user,
                fileName = "Rulebook-2.pdf"
            )

            mockMvc.perform(
                get("/api/v1/games/$gameId/rules")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        fun `should not list files uploaded for a different game`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()
            val otherGameId = UUID.randomUUID()

            uploadPdf(
                otherGameId,
                user
            )

            mockMvc.perform(
                get("/api/v1/games/$gameId/rules")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return an empty list when game has no uploaded files`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()

            mockMvc.perform(
                get("/api/v1/games/$gameId/rules")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/games/{gameId}/rules/{fileId}/download
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/games/{gameId}/rules/{fileId}/download")
    inner class DownloadRuleFileTests {

        @Test
        fun `should stream back the exact uploaded content`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()

            val content =
                "real pdf binary content".toByteArray()

            val uploadResult = mockMvc.perform(
                multipart("/api/v1/games/$gameId/rules")
                    .file(
                        pdfFile(
                            fileName = "Downloadable.pdf",
                            content = content
                        )
                    )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isOk)
                .andReturn()

            val fileId =
                objectIdFromResponse(
                    uploadResult.response.contentAsString
                )

            val downloadResult = mockMvc.perform(
                get(
                    "/api/v1/games/$gameId/rules/$fileId/download"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isOk)
                .andExpect(
                    header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Downloadable.pdf\""
                    )
                )
                .andReturn()

            assertThat(
                downloadResult.response.contentAsByteArray
            ).isEqualTo(content)
        }

        @Test
        fun `should return 404 when file does not exist`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()

            mockMvc.perform(
                get(
                    "/api/v1/games/$gameId/rules/${UUID.randomUUID()}/download"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when file exists but belongs to a different game`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()
            val otherGameId = UUID.randomUUID()

            val uploadResult = mockMvc.perform(
                multipart("/api/v1/games/$otherGameId/rules")
                    .file(pdfFile())
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isOk)
                .andReturn()

            val fileId =
                objectIdFromResponse(
                    uploadResult.response.contentAsString
                )

            mockMvc.perform(
                get(
                    "/api/v1/games/$gameId/rules/$fileId/download"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/games/{gameId}/rules/{fileId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/games/{gameId}/rules/{fileId}")
    inner class DeleteRuleFileTests {

        @Test
        fun `should delete the file and its metadata`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()

            val fileId =
                UUID.fromString(
                    uploadPdf(
                        gameId,
                        user
                    )
                )

            mockMvc.perform(
                delete(
                    "/api/v1/games/$gameId/rules/$fileId"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isNoContent)

            assertThat(
                uploadedFileRepository.findById(fileId)
            ).isEmpty()

            assertThat(storedObjects).isEmpty()
        }

        @Test
        fun `should return 404 when deleting a non-existent file`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()

            mockMvc.perform(
                delete(
                    "/api/v1/games/$gameId/rules/${UUID.randomUUID()}"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should not delete a file uploaded for a different game`() {
            val user = persistUser()
            val gameId = UUID.randomUUID()
            val otherGameId = UUID.randomUUID()

            val fileId =
                UUID.fromString(
                    uploadPdf(
                        otherGameId,
                        user
                    )
                )

            mockMvc.perform(
                delete(
                    "/api/v1/games/$gameId/rules/$fileId"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isNotFound)

            assertThat(
                uploadedFileRepository.findById(fileId)
            ).isPresent
        }
    }
}