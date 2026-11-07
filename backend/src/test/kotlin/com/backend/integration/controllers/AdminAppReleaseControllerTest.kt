package com.backend.integration.controllers

import com.backend.models.entities.AppRelease
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.AppReleaseRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
class AdminAppReleaseControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var appReleaseRepository: AppReleaseRepository

    @Autowired
    private lateinit var jwtService: JwtService

    companion object {
        private const val URL = "/api/v1/admin/app-releases"
    }

    @BeforeEach
    fun setUp() {
        appReleaseRepository.deleteAll()
    }

    @AfterEach
    fun cleanUp() {
        appReleaseRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun persistUser(username: String = "user-${UUID.randomUUID()}", role: UserRole = UserRole.USER): User =
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

    private fun persistRelease(version: String, minSupportedVersion: String): AppRelease =
        appReleaseRepository.saveAndFlush(
            AppRelease(
                version = version,
                minSupportedVersion = minSupportedVersion,
                downloadUrl = "https://example.com/lets-table-$version.apk",
                releaseNotes = null,
            )
        )

    private fun json(value: String?): String = value?.let { "\"${it.replace("\"", "\\\"").replace("\n", "\\n")}\"" } ?: "null"

    private fun body(
        version: String? = "2.4.0",
        minSupportedVersion: String? = null,
        downloadUrl: String? = null,
        releaseNotes: String? = null,
    ): String = """
        {
            "version": ${json(version)},
            "minSupportedVersion": ${json(minSupportedVersion)},
            "downloadUrl": ${json(downloadUrl)},
            "releaseNotes": ${json(releaseNotes)}
        }
    """.trimIndent()

    private fun publish(content: String, user: User? = persistUser(role = UserRole.ADMIN)): ResultActions =
        mockMvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } }
        )

    @Nested
    @DisplayName("POST /api/v1/admin/app-releases")
    inner class CreateReleaseTests {

        @Test
        fun `should publish the release and return 201`() {
            publish(body(version = "2.4.0", minSupportedVersion = "2.3.0", downloadUrl = "https://example.com/app.apk", releaseNotes = "## New Features"))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.latestVersion").value("2.4.0"))
                .andExpect(jsonPath("$.minSupportedVersion").value("2.3.0"))
                .andExpect(jsonPath("$.downloadUrl").value("https://example.com/app.apk"))
                .andExpect(jsonPath("$.releaseNotes").value("## New Features"))

            val saved = appReleaseRepository.findAll().single()
            assertThat(saved.version).isEqualTo("2.4.0")
            assertThat(saved.minSupportedVersion).isEqualTo("2.3.0")
            assertThat(saved.downloadUrl).isEqualTo("https://example.com/app.apk")
            assertThat(saved.releaseNotes).isEqualTo("## New Features")
        }

        @Test
        fun `should build the download url from the version when none is sent`() {
            publish(body(version = "2.4.0", minSupportedVersion = "2.4.0"))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.downloadUrl").value(containsString("2.4.0")))
        }

        @Test
        fun `should store blank release notes as null`() {
            publish(body(version = "2.4.0", minSupportedVersion = "2.4.0", releaseNotes = "   "))
                .andExpect(status().isCreated)

            assertThat(appReleaseRepository.findAll().single().releaseNotes).isNull()
        }

        @Test
        fun `should trim the release notes`() {
            publish(body(version = "2.4.0", minSupportedVersion = "2.4.0", releaseNotes = "\n## Fixes\n- **Login**: fixed\n"))
                .andExpect(status().isCreated)

            assertThat(appReleaseRepository.findAll().single().releaseNotes).isEqualTo("## Fixes\n- **Login**: fixed")
        }

        @Test
        fun `should use the version as minimum for the very first release`() {
            publish(body(version = "1.0.0"))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.minSupportedVersion").value("1.0.0"))
        }

        @Test
        fun `should keep the minimum of the previous release when none is sent`() {
            persistRelease(version = "2.3.0", minSupportedVersion = "2.2.0")

            publish(body(version = "2.4.0"))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.minSupportedVersion").value("2.2.0"))

            assertThat(appReleaseRepository.findAll()).hasSize(2)
        }

        @Test
        fun `should return 400 when the minimum is greater than the version`() {
            publish(body(version = "2.4.0", minSupportedVersion = "2.5.0"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("minSupportedVersion cannot be greater than version"))

            assertThat(appReleaseRepository.findAll()).isEmpty()
        }

        @Test
        fun `should compare versions numerically, not alphabetically`() {
            publish(body(version = "2.10.0", minSupportedVersion = "2.9.0"))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.minSupportedVersion").value("2.9.0"))
        }

        @ParameterizedTest
        @ValueSource(strings = ["latest", "2.x.0", "2..0", "2.4.0-beta"])
        fun `should return 400 for a version that is not in the X_Y_Z format`(version: String) {
            publish(body(version = version))
                .andExpect(status().isBadRequest)

            assertThat(appReleaseRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 for an invalid minimum version`() {
            publish(body(version = "2.4.0", minSupportedVersion = "two"))
                .andExpect(status().isBadRequest)

            assertThat(appReleaseRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 when the version is missing`() {
            publish("{}")
                .andExpect(status().isBadRequest)

            assertThat(appReleaseRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 for a malformed body`() {
            publish("{ not json")
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 409 when the version was already published`() {
            persistRelease(version = "2.4.0", minSupportedVersion = "2.3.0")

            publish(body(version = "2.4.0", minSupportedVersion = "2.3.0"))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.message").value(containsString("2.4.0")))

            assertThat(appReleaseRepository.findAll()).hasSize(1)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            publish(body(), user = persistUser())
                .andExpect(status().isForbidden)

            assertThat(appReleaseRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            publish(body(), user = null)
                .andExpect(status().isForbidden)

            assertThat(appReleaseRepository.findAll()).isEmpty()
        }
    }
}