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
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*

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

    private fun persistUser(
        username: String = "user-${UUID.randomUUID()}",
        role: UserRole = UserRole.USER,
        accountStatus: AccountStatus = AccountStatus.ACTIVE,
    ): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = role,
                accountStatus = accountStatus,
            )
        )

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun imageFile(
        fileName: String = "avatar.png",
        contentType: String = "image/png",
        content: ByteArray = "fake image binary content".toByteArray()
    ) =
        MockMultipartFile(
            "file",
            fileName,
            contentType,
            content
        )

    private fun uploadAvatar(
        user: User,
        fileName: String = "avatar.png",
        contentType: String = "image/png"
    ): String {
        val result = mockMvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(imageFile(fileName = fileName, contentType = contentType))
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
    // POST /api/v1/users/me/avatar
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/users/me/avatar")
    inner class UploadAvatarTests {

        @Test
        fun `should upload avatar image successfully when user is authenticated`() {
            val user = persistUser()

            mockMvc.perform(
                multipart("/api/v1/users/me/avatar")
                    .file(imageFile(fileName = "profile.jpg", contentType = "image/jpeg"))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fileName").value("profile.jpg"))
                .andExpect(jsonPath("$.uploadedByUsername").value(user.username))

            val savedFiles = uploadedFileRepository.findAll()
            assertThat(savedFiles).hasSize(1)
            assertThat(savedFiles[0].uploadedBy?.id).isEqualTo(user.id)
            assertThat(savedFiles[0].ownerType).isEqualTo(FileOwnerType.USER_AVATAR)
            assertThat(savedFiles[0].ownerId).isEqualTo(user.id)
            assertThat(storedObjects).hasSize(1)
        }

        @Test
        fun `should return 400 Bad Request when uploading unsupported file type`() {
            val user = persistUser()

            val pdfFile = MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "%PDF-1.4 dummy pdf content".toByteArray()
            )

            mockMvc.perform(
                multipart("/api/v1/users/me/avatar")
                    .file(pdfFile)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isBadRequest)

            assertThat(uploadedFileRepository.count()).isEqualTo(0)
            assertThat(storedObjects).isEmpty()
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            mockMvc.perform(
                multipart("/api/v1/users/me/avatar")
                    .file(imageFile())
            )
                .andExpect(status().isForbidden)

            assertThat(storedObjects).isEmpty()
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/avatars/{fileId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/avatars/{fileId}")
    inner class GetAvatarTests {

        @Test
        fun `should stream back the exact uploaded avatar content`() {
            val user = persistUser()
            val imageContent = "png binary raw data".toByteArray()

            val uploadResult = mockMvc.perform(
                multipart("/api/v1/users/me/avatar")
                    .file(imageFile(fileName = "avatar.png", contentType = "image/png", content = imageContent))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isOk)
                .andReturn()

            val fileId = objectIdFromResponse(uploadResult.response.contentAsString)

            val downloadResult = mockMvc.perform(
                get("/api/v1/avatars/$fileId")
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn()

            assertThat(downloadResult.response.contentAsByteArray).isEqualTo(imageContent)
        }

        @Test
        fun `should return 404 Not Found when fileId does not exist`() {
            val randomId = UUID.randomUUID()

            mockMvc.perform(get("/api/v1/avatars/$randomId"))
                .andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/users/me/avatar/{fileId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/users/me/avatar/{fileId}")
    inner class DeleteAvatarTests {

        @Test
        fun `should delete file record and remove object from storage`() {
            val user = persistUser()
            val fileId = UUID.fromString(uploadAvatar(user))

            assertThat(uploadedFileRepository.findById(fileId)).isPresent
            assertThat(storedObjects).hasSize(1)

            mockMvc.perform(
                delete("/api/v1/users/me/avatar/$fileId")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isNoContent)

            assertThat(uploadedFileRepository.findById(fileId)).isEmpty
            assertThat(storedObjects).isEmpty()
        }

        @Test
        fun `should return 404 Not Found when deleting a non-existent avatar`() {
            val user = persistUser()

            mockMvc.perform(
                delete("/api/v1/users/me/avatar/${UUID.randomUUID()}")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        authHeader(user)
                    )
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val user = persistUser()
            val fileId = UUID.fromString(uploadAvatar(user))

            mockMvc.perform(
                delete("/api/v1/users/me/avatar/$fileId")
            )
                .andExpect(status().isForbidden)

            assertThat(uploadedFileRepository.findById(fileId)).isPresent
        }
    }
}