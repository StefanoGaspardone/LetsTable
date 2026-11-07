package com.backend.integration.controllers

import com.backend.exceptions.StorageReadException
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
import org.hamcrest.Matchers.containsString
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

@AutoConfigureMockMvc
class UserAvatarControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var uploadedFileRepository: UploadedFileRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    private lateinit var storageService: StorageService

    private val storedObjects = mutableMapOf<String, ByteArray>()
    private val jsonMapper = JsonMapper.builder().build()

    companion object {
        private const val UPLOAD_URL = "/api/v1/users/me/avatar"
        private const val PUBLIC_URL = "/api/v1/avatars"
        private val PNG_CONTENT = "fake png binary content".toByteArray()
    }

    @BeforeEach
    fun setUpStorageMock() {
        storedObjects.clear()

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
        userRepository.findAll().filter { it.avatarId != null }.forEach { userRepository.saveAndFlush(it.apply { avatarId = null }) }
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

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, user: User?): ResultActions =
        mockMvc.perform(request.apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    private fun imageFile(fileName: String = "avatar.png", contentType: String = "image/png", content: ByteArray = PNG_CONTENT) =
        MockMultipartFile("file", fileName, contentType, content)

    private fun upload(user: User?, file: MockMultipartFile = imageFile()): ResultActions =
        perform(multipart(UPLOAD_URL).file(file), user)

    private fun uploadAvatar(user: User, file: MockMultipartFile = imageFile()): UUID {
        val body = upload(user, file).andExpect(status().isOk).andReturn().response.contentAsString
        return UUID.fromString(jsonMapper.readTree(body).get("id").asString())
    }

    private fun deleteAvatar(user: User?, fileId: Any) = perform(delete("$UPLOAD_URL/$fileId"), user)

    private fun setActiveAvatar(user: User, fileId: UUID?) {
        userRepository.saveAndFlush(userRepository.findById(user.id!!).orElseThrow().apply { avatarId = fileId })
    }

    private fun avatarIdOf(user: User): UUID? = userRepository.findById(user.id!!).orElseThrow().avatarId

    @Nested
    @DisplayName("POST /api/v1/users/me/avatar")
    inner class UploadAvatarTests {

        @Test
        fun `should upload avatar image successfully when user is authenticated`() {
            val user = persistUser()

            upload(user, imageFile(fileName = "profile.jpg", contentType = "image/jpeg"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fileName").value("profile.jpg"))
                .andExpect(jsonPath("$.uploadedByUsername").value(user.username))

            val saved = uploadedFileRepository.findAll().single()
            assertThat(saved.uploadedBy?.id).isEqualTo(user.id)
            assertThat(saved.ownerType).isEqualTo(FileOwnerType.USER_AVATAR)
            assertThat(saved.ownerId).isEqualTo(user.id)
            assertThat(storedObjects.keys.single()).startsWith("user_avatar/${user.id}/")
            assertThat(storedObjects.values.single()).isEqualTo(PNG_CONTENT)
        }

        @Test
        fun `should not change the active avatar on upload`() {
            val user = persistUser()

            uploadAvatar(user)

            assertThat(avatarIdOf(user)).isNull()
        }

        @Test
        fun `should keep every uploaded file as a separate object`() {
            val user = persistUser()

            val first = uploadAvatar(user)
            val second = uploadAvatar(user, imageFile(fileName = "second.webp", contentType = "image/webp"))

            assertThat(first).isNotEqualTo(second)
            assertThat(uploadedFileRepository.findAll()).hasSize(2)
            assertThat(storedObjects).hasSize(2)
        }

        @Test
        fun `should strip the path from the uploaded file name`() {
            val user = persistUser()

            upload(user, imageFile(fileName = "../../etc/evil.png"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.fileName").value("evil.png"))

            assertThat(storedObjects.keys.single()).doesNotContain("..")
        }

        @ParameterizedTest
        @CsvSource(
            "document.pdf, application/pdf",
            "notes.txt, text/plain",
            "avatar.svg, image/svg+xml",
            "video.mp4, video/mp4",
        )
        fun `should return 400 when uploading an unsupported file type`(fileName: String, contentType: String) {
            upload(persistUser(), imageFile(fileName = fileName, contentType = contentType))
                .andExpect(status().isBadRequest)

            assertThat(uploadedFileRepository.count()).isZero()
            assertThat(storedObjects).isEmpty()
        }

        @Test
        fun `should return 400 when the file is empty`() {
            upload(persistUser(), imageFile(content = ByteArray(0)))
                .andExpect(status().isBadRequest)

            assertThat(uploadedFileRepository.count()).isZero()
            assertThat(storedObjects).isEmpty()
        }

        @Test
        fun `should return 400 when the file part is missing`() {
            perform(multipart(UPLOAD_URL), persistUser()).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 502 and save nothing when the storage write fails`() {
            willThrow(StorageWriteException("user_avatar/key.png")).given(storageService).putObject(anyString(), any<InputStream>(), anyLong(), anyString())

            upload(persistUser()).andExpect(status().isBadGateway)

            assertThat(uploadedFileRepository.count()).isZero()
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            upload(null).andExpect(status().isForbidden)

            assertThat(storedObjects).isEmpty()
        }
    }

    @Nested
    @DisplayName("GET /api/v1/avatars/{fileId}")
    inner class GetAvatarTests {

        @Test
        fun `should stream back the exact uploaded avatar content without authentication`() {
            val fileId = uploadAvatar(persistUser())

            val response = perform(get("$PUBLIC_URL/$fileId"), null)
                .andExpect(status().isOk)
                .andExpect(content().contentType("image/png"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PNG_CONTENT.size.toLong()))
                .andReturn().response

            assertThat(response.contentAsByteArray).isEqualTo(PNG_CONTENT)
        }

        @Test
        fun `should let another user read the avatar`() {
            val fileId = uploadAvatar(persistUser(username = "owner"))

            perform(get("$PUBLIC_URL/$fileId"), persistUser(username = "viewer")).andExpect(status().isOk)
        }

        @Test
        fun `should return a private cache header of seven days`() {
            val fileId = uploadAvatar(persistUser())

            perform(get("$PUBLIC_URL/$fileId"), null)
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, allOf(containsString("max-age=604800"), containsString("private"))))
        }

        @Test
        fun `should return the content type of the uploaded file`() {
            val fileId = uploadAvatar(persistUser(), imageFile(fileName = "avatar.webp", contentType = "image/webp"))

            perform(get("$PUBLIC_URL/$fileId"), null).andExpect(content().contentType("image/webp"))
        }

        @Test
        fun `should return 404 when the file is not an avatar`() {
            val fileId = uploadAvatar(persistUser())
            val otherType = FileOwnerType.entries.first { it != FileOwnerType.USER_AVATAR }
            jdbcTemplate.update("UPDATE uploaded_files SET owner_type = ? WHERE id = ?", otherType.name, fileId)

            perform(get("$PUBLIC_URL/$fileId"), null).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when fileId does not exist`() {
            perform(get("$PUBLIC_URL/${UUID.randomUUID()}"), null).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when fileId is not a UUID`() {
            perform(get("$PUBLIC_URL/not-a-uuid"), null).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 502 when the storage read fails`() {
            val fileId = uploadAvatar(persistUser())
            willThrow(StorageReadException("user_avatar/key.png")).given(storageService).getObject(anyString())

            perform(get("$PUBLIC_URL/$fileId"), null).andExpect(status().isBadGateway)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me/avatar/{fileId}")
    inner class DeleteAvatarTests {

        @Test
        fun `should delete file record and remove object from storage`() {
            val user = persistUser()
            val fileId = uploadAvatar(user)

            deleteAvatar(user, fileId).andExpect(status().isNoContent)

            assertThat(uploadedFileRepository.findById(fileId)).isEmpty
            assertThat(storedObjects).isEmpty()
            perform(get("$PUBLIC_URL/$fileId"), null).andExpect(status().isNotFound)
        }

        @Test
        fun `should clear the active avatar when deleting it`() {
            val user = persistUser()
            val fileId = uploadAvatar(user)
            setActiveAvatar(user, fileId)

            deleteAvatar(user, fileId).andExpect(status().isNoContent)

            assertThat(avatarIdOf(user)).isNull()
            assertThat(uploadedFileRepository.findById(fileId)).isEmpty
        }

        @Test
        fun `should keep the active avatar when deleting another one`() {
            val user = persistUser()
            val active = uploadAvatar(user)
            val old = uploadAvatar(user)
            setActiveAvatar(user, active)

            deleteAvatar(user, old).andExpect(status().isNoContent)

            assertThat(avatarIdOf(user)).isEqualTo(active)
            assertThat(uploadedFileRepository.findById(active)).isPresent
            assertThat(storedObjects).hasSize(1)
        }

        @Test
        fun `should return 404 and keep the file when it belongs to another user`() {
            val owner = persistUser(username = "owner")
            val intruder = persistUser(username = "intruder")
            val fileId = uploadAvatar(owner)
            setActiveAvatar(owner, fileId)

            deleteAvatar(intruder, fileId).andExpect(status().isNotFound)

            assertThat(uploadedFileRepository.findById(fileId)).isPresent
            assertThat(storedObjects).hasSize(1)
            assertThat(avatarIdOf(owner)).isEqualTo(fileId)
        }

        @Test
        fun `should return 404 when the file is not an avatar`() {
            val user = persistUser()
            val fileId = uploadAvatar(user)
            val otherType = FileOwnerType.entries.first { it != FileOwnerType.USER_AVATAR }
            jdbcTemplate.update("UPDATE uploaded_files SET owner_type = ? WHERE id = ?", otherType.name, fileId)

            deleteAvatar(user, fileId).andExpect(status().isNotFound)

            assertThat(uploadedFileRepository.findById(fileId)).isPresent
        }

        @Test
        fun `should return 404 when deleting a non-existent avatar`() {
            deleteAvatar(persistUser(), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when fileId is not a UUID`() {
            deleteAvatar(persistUser(), "not-a-uuid").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val fileId = uploadAvatar(persistUser())

            deleteAvatar(null, fileId).andExpect(status().isForbidden)

            assertThat(uploadedFileRepository.findById(fileId)).isPresent
        }
    }
}