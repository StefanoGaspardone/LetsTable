package com.backend.integration.controllers

import com.backend.models.entities.AppRelease
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.AppReleaseRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
class AppControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var appReleaseRepository: AppReleaseRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var jwtService: JwtService

    companion object {
        private const val URL = "/api/v1/app/version"
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

    private fun persistRelease(version: String, minSupportedVersion: String = version, releaseNotes: String? = null): AppRelease =
        appReleaseRepository.saveAndFlush(
            AppRelease(
                version = version,
                minSupportedVersion = minSupportedVersion,
                downloadUrl = "https://example.com/lets-table-$version.apk",
                releaseNotes = releaseNotes,
            )
        )

    private fun persistUser(username: String = "user-${UUID.randomUUID()}"): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = UserRole.USER,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

    @Nested
    @DisplayName("GET /api/v1/app/version")
    inner class GetLatestVersionTests {

        @Test
        fun `should return the latest published release`() {
            persistRelease(version = "2.4.0", minSupportedVersion = "2.3.0", releaseNotes = "## New Features\n- **Catalog**: browse all games")

            mockMvc.perform(get(URL))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.latestVersion").value("2.4.0"))
                .andExpect(jsonPath("$.minSupportedVersion").value("2.3.0"))
                .andExpect(jsonPath("$.downloadUrl").value("https://example.com/lets-table-2.4.0.apk"))
                .andExpect(jsonPath("$.releaseNotes").value("## New Features\n- **Catalog**: browse all games"))
                .andExpect(jsonPath("$.publishedAt").exists())
        }

        @Test
        fun `should return the most recently published release`() {
            persistRelease(version = "2.3.0")
            persistRelease(version = "2.4.0", minSupportedVersion = "2.3.0")

            mockMvc.perform(get(URL))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.latestVersion").value("2.4.0"))
        }

        @Test
        fun `should return null release notes when none were written`() {
            persistRelease(version = "2.4.0")

            mockMvc.perform(get(URL))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.releaseNotes").doesNotExist())
        }

        @Test
        fun `should return 404 when no release has been published yet`() {
            mockMvc.perform(get(URL))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("No app release published yet"))
        }

        @Test
        fun `should be reachable without authentication`() {
            persistRelease(version = "2.4.0")

            mockMvc.perform(get(URL))
                .andExpect(status().isOk)
        }

        @Test
        fun `should be reachable by an authenticated user too`() {
            persistRelease(version = "2.4.0")
            val user = persistUser()

            mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.latestVersion").value("2.4.0"))
        }

        @Test
        fun `should still answer with an expired or invalid token`() {
            persistRelease(version = "2.4.0")

            mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isOk)
        }

        @Test
        fun `should not accept other methods`() {
            mockMvc.perform(post(URL))
                .andExpect(status().is4xxClientError)
        }
    }
}