package com.backend.models.dtos

import com.backend.models.entities.AppRelease
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(description = "Latest available version of the mobile app")
data class AppVersionDTO(
    @field:Schema(description = "Latest published version", example = "2.2.0")
    val latestVersion: String,

    @field:Schema(description = "Oldest version still allowed to use the app; older versions must update", example = "2.2.0")
    val minSupportedVersion: String,

    @field:Schema(description = "URL to download the APK of the latest version", example = "https://example.com/lets-table.apk")
    val downloadUrl: String,

    @field:Schema(description = "Release notes in markdown: '## Section' headings and '- **Title**: description' bullets", nullable = true)
    val releaseNotes: String?,

    @field:Schema(description = "When the version was published", example = "2026-11-03T12:00:00Z")
    val publishedAt: Instant,
) {
    companion object {
        fun from(release: AppRelease) = AppVersionDTO(
            latestVersion = release.version,
            minSupportedVersion = release.minSupportedVersion,
            downloadUrl = release.downloadUrl,
            releaseNotes = release.releaseNotes,
            publishedAt = release.createdAt,
        )
    }
}

@Schema(description = "Payload to publish a new version of the mobile app")
data class CreateAppReleaseRequest(
    @field:Schema(description = "Version in MAJOR.MINOR.PATCH format", example = "2.3.0")
    @field:NotBlank
    @field:Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "must be in MAJOR.MINOR.PATCH format")
    val version: String,

    @field:Schema(description = "Oldest version still supported, must not be greater than version. If omitted, the one of the previous release is kept", example = "2.3.0", nullable = true)
    @field:Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "must be in MAJOR.MINOR.PATCH format")
    val minSupportedVersion: String? = null,

    @field:Schema(description = "HTTPS URL of the APK. If omitted, it is built from the version using the GitHub releases template", example = "https://github.com/StefanoGaspardone/LetsTable/releases/download/v2.3.0/lets-table-2.3.0.apk", nullable = true)
    @field:Size(max = 500)
    @field:Pattern(regexp = "^https://.+", message = "must be an https URL")
    val downloadUrl: String? = null,

    @field:Schema(description = "Release notes in markdown: '## Section' headings and '- **Title**: description' bullets", nullable = true)
    @field:Size(max = 10000)
    val releaseNotes: String? = null,
)