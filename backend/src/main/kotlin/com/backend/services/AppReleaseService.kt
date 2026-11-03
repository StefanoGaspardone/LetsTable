package com.backend.services

import com.backend.exceptions.AppReleaseAlreadyExistsException
import com.backend.exceptions.AppReleaseNotFoundException
import com.backend.exceptions.InvalidAppReleaseException
import com.backend.models.dtos.AppVersionDTO
import com.backend.models.dtos.CreateAppReleaseRequest
import com.backend.models.entities.AppRelease
import com.backend.repositories.AppReleaseRepository
import com.backend.utils.compareVersions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppReleaseService(
    private val appReleaseRepository: AppReleaseRepository,
    @Value($$"${app.release.download-url-template}") private val downloadUrlTemplate: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun getLatestRelease(): AppVersionDTO {
        logger.debug("\n\t[DEBUG] [app_release_service][get_latest_release] Retrieving latest app release")

        try {
            val release = appReleaseRepository.findFirstByOrderByCreatedAtDesc()
                .orElseThrow { AppReleaseNotFoundException() }

            logger.info("\n\t[INFO] [app_release_service][get_latest_release] Latest app release is {}", release.version)
            return AppVersionDTO.from(release)
        } catch(e: AppReleaseNotFoundException) {
            logger.warn("\n\t[WARN] [app_release_service][get_latest_release] No app release published yet")
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [app_release_service][get_latest_release] Error retrieving latest app release: {}", e.message)
            throw e
        }
    }

    @Transactional
    fun createRelease(request: CreateAppReleaseRequest): AppVersionDTO {
        logger.debug("\n\t[DEBUG] [app_release_service][create_release] Publishing app release {}", request.version)

        try {
            val minSupportedVersion = request.minSupportedVersion
                ?: appReleaseRepository.findFirstByOrderByCreatedAtDesc().map { it.minSupportedVersion }.orElse(request.version)

            if(compareVersions(minSupportedVersion, request.version) > 0) {
                throw InvalidAppReleaseException("minSupportedVersion cannot be greater than version")
            }

            if(appReleaseRepository.existsByVersion(request.version)) {
                throw AppReleaseAlreadyExistsException(request.version)
            }

            val saved = appReleaseRepository.save(
                AppRelease(
                    version = request.version,
                    minSupportedVersion = minSupportedVersion,
                    downloadUrl = request.downloadUrl?.trim()?.ifBlank { null } ?: buildDownloadUrl(request.version),
                    releaseNotes = request.releaseNotes?.trim()?.ifBlank { null },
                )
            )

            logger.info("\n\t[INFO] [app_release_service][create_release] App release {} published, min supported {}", saved.version, saved.minSupportedVersion)
            return AppVersionDTO.from(saved)
        } catch(e: InvalidAppReleaseException) {
            logger.warn("\n\t[WARN] [app_release_service][create_release] Invalid app release {}: {}", request.version, e.message)
            throw e
        } catch(e: AppReleaseAlreadyExistsException) {
            logger.warn("\n\t[WARN] [app_release_service][create_release] App release {} already exists", request.version)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [app_release_service][create_release] Error publishing app release {}: {}", request.version, e.message)
            throw e
        }
    }

    private fun buildDownloadUrl(version: String): String =
        downloadUrlTemplate.replace("{version}", version)
}