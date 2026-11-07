package com.backend.unit.services

import com.backend.exceptions.AppReleaseAlreadyExistsException
import com.backend.exceptions.AppReleaseNotFoundException
import com.backend.exceptions.InvalidAppReleaseException
import com.backend.models.dtos.AppVersionDTO
import com.backend.models.dtos.CreateAppReleaseRequest
import com.backend.models.entities.AppRelease
import com.backend.repositories.AppReleaseRepository
import com.backend.services.AppReleaseService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.dao.DataIntegrityViolationException
import java.util.Optional

class AppReleaseServiceTest {

    private lateinit var appReleaseRepository: AppReleaseRepository
    private lateinit var appReleaseService: AppReleaseService

    private val savedRelease = slot<AppRelease>()

    companion object {
        private const val URL_TEMPLATE = "https://github.com/StefanoGaspardone/LetsTable/releases/download/v{version}/lets-table-{version}.apk"
    }

    @BeforeEach
    fun setUp() {
        appReleaseRepository = mockk()
        appReleaseService = AppReleaseService(appReleaseRepository, URL_TEMPLATE)
    }

    private fun release(version: String, minSupportedVersion: String = version, releaseNotes: String? = null) =
        AppRelease(
            version = version,
            minSupportedVersion = minSupportedVersion,
            downloadUrl = URL_TEMPLATE.replace("{version}", version),
            releaseNotes = releaseNotes,
        )

    private fun request(
        version: String = "2.4.0",
        minSupportedVersion: String? = null,
        downloadUrl: String? = null,
        releaseNotes: String? = null,
    ) = CreateAppReleaseRequest(
        version = version,
        minSupportedVersion = minSupportedVersion,
        downloadUrl = downloadUrl,
        releaseNotes = releaseNotes,
    )

    private fun stubCreate(previous: AppRelease? = null, existingVersions: Set<String> = emptySet()) {
        every { appReleaseRepository.findFirstByOrderByCreatedAtDesc() } returns Optional.ofNullable(previous)
        every { appReleaseRepository.existsByVersion(any()) } answers { firstArg<String>() in existingVersions }
        every { appReleaseRepository.saveAndFlush(capture(savedRelease)) } answers { savedRelease.captured }
    }

    @Nested
    @DisplayName("getLatestRelease")
    inner class GetLatestReleaseTests {

        @Test
        fun `should return the most recently published release`() {
            val latest = release("2.4.0", "2.3.0", "## New Features")
            every { appReleaseRepository.findFirstByOrderByCreatedAtDesc() } returns Optional.of(latest)

            assertThat(appReleaseService.getLatestRelease()).isEqualTo(AppVersionDTO.from(latest))
        }

        @Test
        fun `should throw AppReleaseNotFoundException when nothing has been published`() {
            every { appReleaseRepository.findFirstByOrderByCreatedAtDesc() } returns Optional.empty()

            assertThatThrownBy { appReleaseService.getLatestRelease() }
                .isInstanceOf(AppReleaseNotFoundException::class.java)
        }

        @Test
        fun `should propagate repository errors`() {
            every { appReleaseRepository.findFirstByOrderByCreatedAtDesc() } throws RuntimeException("DB error")

            assertThatThrownBy { appReleaseService.getLatestRelease() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }
    }

    @Nested
    @DisplayName("createRelease - versions")
    inner class VersionTests {

        @Test
        fun `should save the release and return it`() {
            stubCreate(previous = release("2.3.0"))

            val result = appReleaseService.createRelease(request(version = "2.4.0", minSupportedVersion = "2.3.0"))

            assertThat(savedRelease.captured.version).isEqualTo("2.4.0")
            assertThat(savedRelease.captured.minSupportedVersion).isEqualTo("2.3.0")
            assertThat(result).isEqualTo(AppVersionDTO.from(savedRelease.captured))
        }

        @ParameterizedTest
        @ValueSource(strings = ["v2.4.0", "V2.4.0", " 2.4.0 ", " v2.4.0\n"])
        fun `should normalize the version coming from a git tag`(raw: String) {
            stubCreate()

            appReleaseService.createRelease(request(version = raw))

            assertThat(savedRelease.captured.version).isEqualTo("2.4.0")
            assertThat(savedRelease.captured.downloadUrl).isEqualTo("https://github.com/StefanoGaspardone/LetsTable/releases/download/v2.4.0/lets-table-2.4.0.apk")
        }

        @ParameterizedTest
        @ValueSource(strings = ["2.4", "2.4.0.1", "10.20.30"])
        fun `should accept versions with two to four numeric parts`(version: String) {
            stubCreate()

            appReleaseService.createRelease(request(version = version))

            assertThat(savedRelease.captured.version).isEqualTo(version)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   ", "2", "2.4.0-beta", "latest", "2.x.0", "2..0", "2.4.0.1.2"])
        fun `should reject versions that are not numeric`(version: String) {
            stubCreate()

            assertThatThrownBy { appReleaseService.createRelease(request(version = version)) }
                .isInstanceOf(InvalidAppReleaseException::class.java)

            verify(exactly = 0) { appReleaseRepository.saveAndFlush(any()) }
        }

        @Test
        fun `should reject a malformed minimum supported version`() {
            stubCreate()

            assertThatThrownBy { appReleaseService.createRelease(request(version = "2.4.0", minSupportedVersion = "two")) }
                .isInstanceOf(InvalidAppReleaseException::class.java)
        }
    }

    @Nested
    @DisplayName("createRelease - minimum supported version")
    inner class MinSupportedVersionTests {

        @Test
        fun `should use the requested minimum supported version`() {
            stubCreate(previous = release("2.3.0", "2.0.0"))

            appReleaseService.createRelease(request(version = "2.4.0", minSupportedVersion = "v2.3.0"))

            assertThat(savedRelease.captured.minSupportedVersion).isEqualTo("2.3.0")
        }

        @Test
        fun `should inherit the minimum supported version of the previous release when none is given`() {
            stubCreate(previous = release("2.3.1", "2.3.0"))

            appReleaseService.createRelease(request(version = "2.4.0"))

            assertThat(savedRelease.captured.minSupportedVersion).isEqualTo("2.3.0")
        }

        @Test
        fun `should treat a blank minimum supported version as not given`() {
            stubCreate(previous = release("2.3.1", "2.3.0"))

            appReleaseService.createRelease(request(version = "2.4.0", minSupportedVersion = "  "))

            assertThat(savedRelease.captured.minSupportedVersion).isEqualTo("2.3.0")
        }

        @Test
        fun `should use the release itself as minimum for the very first release`() {
            stubCreate(previous = null)

            appReleaseService.createRelease(request(version = "2.3.0"))

            assertThat(savedRelease.captured.minSupportedVersion).isEqualTo("2.3.0")
        }

        @Test
        fun `should allow forcing the update to the release itself`() {
            stubCreate(previous = release("2.3.0"))

            appReleaseService.createRelease(request(version = "2.4.0", minSupportedVersion = "2.4.0"))

            assertThat(savedRelease.captured.minSupportedVersion).isEqualTo("2.4.0")
        }

        @Test
        fun `should reject a minimum supported version greater than the release`() {
            stubCreate()

            assertThatThrownBy { appReleaseService.createRelease(request(version = "2.4.0", minSupportedVersion = "2.10.0")) }
                .isInstanceOf(InvalidAppReleaseException::class.java)
                .hasMessageContaining("cannot be greater")

            verify(exactly = 0) { appReleaseRepository.saveAndFlush(any()) }
        }
    }

    @Nested
    @DisplayName("createRelease - download url and notes")
    inner class DownloadUrlTests {

        @Test
        fun `should build the download url from the template when none is given`() {
            stubCreate()

            appReleaseService.createRelease(request(version = "2.4.0"))

            assertThat(savedRelease.captured.downloadUrl).isEqualTo("https://github.com/StefanoGaspardone/LetsTable/releases/download/v2.4.0/lets-table-2.4.0.apk")
        }

        @Test
        fun `should keep an explicit download url`() {
            stubCreate()

            appReleaseService.createRelease(request(downloadUrl = "  https://example.com/custom.apk  "))

            assertThat(savedRelease.captured.downloadUrl).isEqualTo("https://example.com/custom.apk")
        }

        @Test
        fun `should fall back to the template when the download url is blank`() {
            stubCreate()

            appReleaseService.createRelease(request(version = "2.4.0", downloadUrl = "   "))

            assertThat(savedRelease.captured.downloadUrl).endsWith("/v2.4.0/lets-table-2.4.0.apk")
        }

        @Test
        fun `should trim release notes and drop blank ones`() {
            stubCreate()
            appReleaseService.createRelease(request(version = "2.4.0", releaseNotes = "\n# Let's Table - v2.4.0\n\n"))
            assertThat(savedRelease.captured.releaseNotes).isEqualTo("# Let's Table - v2.4.0")

            stubCreate()
            appReleaseService.createRelease(request(version = "2.4.1", releaseNotes = "   "))
            assertThat(savedRelease.captured.releaseNotes).isNull()
        }
    }

    @Nested
    @DisplayName("createRelease - duplicates")
    inner class DuplicateTests {

        @Test
        fun `should reject a version that has already been published`() {
            stubCreate(previous = release("2.4.0"), existingVersions = setOf("2.4.0"))

            assertThatThrownBy { appReleaseService.createRelease(request(version = "v2.4.0")) }
                .isInstanceOf(AppReleaseAlreadyExistsException::class.java)

            verify(exactly = 0) { appReleaseRepository.saveAndFlush(any()) }
        }

        @Test
        fun `should map a concurrent insert of the same version to AppReleaseAlreadyExistsException`() {
            stubCreate()
            every { appReleaseRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate key value violates unique constraint")

            assertThatThrownBy { appReleaseService.createRelease(request(version = "2.4.0")) }
                .isInstanceOf(AppReleaseAlreadyExistsException::class.java)
        }

        @Test
        fun `should propagate unexpected repository errors`() {
            stubCreate()
            every { appReleaseRepository.saveAndFlush(any()) } throws RuntimeException("DB error")

            assertThatThrownBy { appReleaseService.createRelease(request(version = "2.4.0")) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }
    }
}