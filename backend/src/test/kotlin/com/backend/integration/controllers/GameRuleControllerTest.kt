package com.backend.integration.controllers

import com.backend.exceptions.StorageWriteException
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FileOwnerType
import com.backend.models.enums.UserRole
import com.backend.repositories.UploadedFileRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import com.backend.services.StorageService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willAnswer
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

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
    private val jsonMapper = JsonMapper.builder().build()
    private val gameId: UUID = UUID.randomUUID()

    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        storedObjects.clear()
        user = persistUser()

        willAnswer {
            storedObjects[it.getArgument(0)] = it.getArgument<InputStream>(1).readBytes()
            null
        }.given(storageService).putObject(anyString(), any<InputStream>(), anyLong(), anyString())

        given(storageService.getObject(anyString())).willAnswer {
            ByteArrayInputStream(storedObjects[it.getArgument(0)] ?: error("Object not found in test storage: ${it.getArgument<String>(0)}"))
        }

        willAnswer {
            storedObjects.remove(it.getArgument<String>(0))
            null
        }.given(storageService).deleteObject(anyString())
    }

    @AfterEach
    fun cleanUp() {
        storedObjects.clear()
        uploadedFileRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun persistUser(username: String = "stefano", role: UserRole = UserRole.USER): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = role,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

    private fun admin(): User = persistUser(username = "admin-${UUID.randomUUID()}", role = UserRole.ADMIN)

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun file(name: String = "Rulebook.pdf", contentType: String = "application/pdf", content: ByteArray = "fake pdf content".toByteArray()) =
        MockMultipartFile("file", name, contentType, content)

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, as_: User? = user): ResultActions =
        mockMvc.perform(request.apply { as_?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    private fun upload(file: MockMultipartFile = file(), game: UUID = gameId, as_: User? = user): ResultActions =
        perform(multipart("/api/v1/games/$game/rules").file(file), as_)

    private fun uploadedId(file: MockMultipartFile = file(), game: UUID = gameId): UUID {
        val body = upload(file, game).andExpect(status().isOk).andReturn().response.contentAsString
        return UUID.fromString(jsonMapper.readTree(body).get("id").asString())
    }

    private fun filesOf(game: UUID = gameId) = uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.GAME_RULE, game)

    @Nested
    @DisplayName("POST /api/v1/games/{gameId}/rules")
    inner class UploadRuleFileTests {

        @Test
        fun `should upload a PDF file and persist its metadata`() {
            val content = "fake pdf content".toByteArray()

            upload(file(name = "Dune-Imperium-Rulebook.pdf", content = content))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.fileName").value("Dune-Imperium-Rulebook.pdf"))
                .andExpect(jsonPath("$.uploadedByUsername").value("stefano"))

            val saved = filesOf().single()
            assertThat(saved.fileName).isEqualTo("Dune-Imperium-Rulebook.pdf")
            assertThat(saved.contentType).isEqualTo("application/pdf")
            assertThat(saved.size).isEqualTo(content.size.toLong())
            assertThat(saved.uploadedBy?.id).isEqualTo(user.id)
            assertThat(saved.objectKey).startsWith("game_rule/$gameId/").endsWith(".pdf")
            assertThat(storedObjects[saved.objectKey]).isEqualTo(content)
        }

        @ParameterizedTest
        @CsvSource(
            "reference-card.png, image/png",
            "setup.jpg, image/jpeg",
            "how-to-play.mp4, video/mp4",
            "rules.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "scoring.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "summary.pptx, application/vnd.openxmlformats-officedocument.presentationml.presentation",
        )
        fun `should accept the supported file types`(name: String, contentType: String) {
            upload(file(name = name, contentType = contentType))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.fileName").value(name))
        }

        @ParameterizedTest
        @CsvSource(
            "archive.zip, application/zip",
            "script.sh, text/x-shellscript",
            "page.html, text/html",
            "notes.txt, text/plain",
        )
        fun `should return 400 when the file type is not supported`(name: String, contentType: String) {
            upload(file(name = name, contentType = contentType))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Unsupported file type: $contentType"))

            assertThat(filesOf()).isEmpty()
            assertThat(storedObjects).isEmpty()
        }

        @Test
        fun `should refuse SVG images`() {
            upload(file(name = "diagram.svg", contentType = "image/svg+xml", content = "<svg onload=\"alert(1)\"/>".toByteArray()))
                .andExpect(status().isBadRequest)

            assertThat(storedObjects).isEmpty()
        }

        @Test
        fun `should return 400 when the file is empty`() {
            upload(file(content = ByteArray(0)))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Uploaded file is empty"))

            assertThat(filesOf()).isEmpty()
        }

        @Test
        fun `should return 400 when the file part is missing`() {
            perform(multipart("/api/v1/games/$gameId/rules"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the game id is not a UUID`() {
            perform(multipart("/api/v1/games/not-a-uuid/rules").file(file()))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 502 and save nothing when the storage fails`() {
            willThrow(StorageWriteException("game_rule/key.pdf")).given(storageService).putObject(anyString(), any<InputStream>(), anyLong(), anyString())

            upload().andExpect(status().isBadGateway)

            assertThat(filesOf()).isEmpty()
        }

        @Test
        fun `should keep the name of the uploader but not the path of the file`() {
            upload(file(name = "C:\\fakepath\\Rulebook.pdf"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.fileName").value("Rulebook.pdf"))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            upload(as_ = null).andExpect(status().isForbidden)

            assertThat(storedObjects).isEmpty()
            assertThat(filesOf()).isEmpty()
        }
    }

    @Nested
    @DisplayName("GET /api/v1/games/{gameId}/rules")
    inner class ListRuleFilesTests {

        @Test
        fun `should list the uploaded files of a game newest first`() {
            uploadedId(file(name = "Rulebook-1.pdf"))
            uploadedId(file(name = "Rulebook-2.pdf"))

            perform(get("/api/v1/games/$gameId/rules"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].fileName").value(contains("Rulebook-2.pdf", "Rulebook-1.pdf")))
        }

        @Test
        fun `should show files uploaded by other users too`() {
            val marco = persistUser(username = "marco")
            upload(file(name = "Marco-Rules.pdf"), as_ = marco).andExpect(status().isOk)

            perform(get("/api/v1/games/$gameId/rules"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].uploadedByUsername").value("marco"))
        }

        @Test
        fun `should not list files uploaded for a different game`() {
            uploadedId(game = UUID.randomUUID())

            perform(get("/api/v1/games/$gameId/rules"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return an empty list when game has no uploaded files`() {
            perform(get("/api/v1/games/$gameId/rules"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get("/api/v1/games/$gameId/rules"), as_ = null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/games/{gameId}/rules/{fileId}/download")
    inner class DownloadRuleFileTests {

        @Test
        fun `should stream back the exact uploaded content as an attachment`() {
            val content = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x7F, -1, -128)
            val fileId = uploadedId(file(name = "Downloadable.pdf", content = content))

            val response = perform(get("/api/v1/games/$gameId/rules/$fileId/download"))
                .andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, allOf(startsWith("attachment"), containsString("Downloadable.pdf"))))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, content.size.toLong()))
                .andReturn()
                .response

            assertThat(response.contentAsByteArray).isEqualTo(content)
        }

        @Test
        fun `should encode a file name with accented characters`() {
            val fileId = uploadedId(file(name = "Règles-Ark-Nova.pdf"))

            perform(get("/api/v1/games/$gameId/rules/$fileId/download"))
                .andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("R%C3%A8gles-Ark-Nova.pdf")))
        }

        @Test
        fun `should return 404 when file does not exist`() {
            perform(get("/api/v1/games/$gameId/rules/${UUID.randomUUID()}/download"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when the file belongs to another game`() {
            val fileId = uploadedId(game = UUID.randomUUID())

            perform(get("/api/v1/games/$gameId/rules/$fileId/download"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val fileId = uploadedId()

            perform(get("/api/v1/games/$gameId/rules/$fileId/download"), as_ = null)
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/games/{gameId}/rules/{fileId}")
    inner class DeleteRuleFileTests {

        @Test
        fun `should let an admin delete the file and its metadata`() {
            val fileId = uploadedId()

            perform(delete("/api/v1/games/$gameId/rules/$fileId"), as_ = admin())
                .andExpect(status().isNoContent)

            assertThat(uploadedFileRepository.findById(fileId)).isEmpty
            assertThat(storedObjects).isEmpty()
        }

        @Test
        fun `should not let a regular user delete a file, not even their own`() {
            val fileId = uploadedId()

            perform(delete("/api/v1/games/$gameId/rules/$fileId"))
                .andExpect(status().isForbidden)

            assertThat(uploadedFileRepository.findById(fileId)).isPresent
            assertThat(storedObjects).hasSize(1)
        }

        @Test
        fun `should return 404 when deleting a non-existent file`() {
            perform(delete("/api/v1/games/$gameId/rules/${UUID.randomUUID()}"), as_ = admin())
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should not delete a file uploaded for a different game`() {
            val fileId = uploadedId(game = UUID.randomUUID())

            perform(delete("/api/v1/games/$gameId/rules/$fileId"), as_ = admin())
                .andExpect(status().isNotFound)

            assertThat(uploadedFileRepository.findById(fileId)).isPresent
            assertThat(storedObjects).hasSize(1)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val fileId = uploadedId()

            perform(delete("/api/v1/games/$gameId/rules/$fileId"), as_ = null)
                .andExpect(status().isForbidden)

            assertThat(uploadedFileRepository.findById(fileId)).isPresent
        }
    }
}