package com.backend.services

import com.backend.exceptions.InvalidFileTypeException
import com.backend.exceptions.StorageNotFoundException
import com.backend.exceptions.UploadedFileNotFoundException
import com.backend.models.dtos.UploadedFileDTO
import com.backend.models.entities.UploadedFile
import com.backend.models.enums.FileOwnerType
import com.backend.repositories.UploadedFileRepository
import com.backend.repositories.UserRepository
import com.backend.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class UploadedFileService(
    private val uploadedFileRepository: UploadedFileRepository,
    private val userRepository: UserRepository,
    private val storageService: StorageService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
        private const val DEFAULT_FILE_NAME = "file"
        private const val MAX_FILE_NAME_LENGTH = 255
        private val EXTENSION_REGEX = Regex("^[A-Za-z0-9]{1,10}$")
    }

    fun uploadFile(ownerType: FileOwnerType, ownerId: UUID, file: MultipartFile, isContentTypeAllowed: (String?) -> Boolean): UploadedFileDTO {
        logger.debug("\n\t[DEBUG] [uploaded_file_service][upload_file] Uploading file\n\townerType={}\n\townerId={}\n\tfileName={}", ownerType, ownerId, file.originalFilename)

        try {
            if(file.isEmpty) {
                throw InvalidFileTypeException("Uploaded file is empty")
            }

            if(!isContentTypeAllowed(file.contentType)) {
                throw InvalidFileTypeException("Unsupported file type: ${file.contentType}")
            }

            val contentType = file.contentType ?: DEFAULT_CONTENT_TYPE
            val fileName = file.originalFilename?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_FILE_NAME_LENGTH) ?: DEFAULT_FILE_NAME
            val objectKey = buildObjectKey(ownerType, ownerId, fileName)
            val uploadedBy = userRepository.findById(CurrentUser.id()).orElse(null)

            file.inputStream.use { storageService.putObject(objectKey, it, file.size, contentType) }

            val saved = try {
                uploadedFileRepository.save(
                    UploadedFile(
                        ownerType = ownerType,
                        ownerId = ownerId,
                        uploadedBy = uploadedBy,
                        fileName = fileName,
                        objectKey = objectKey,
                        contentType = contentType,
                        size = file.size,
                    )
                )
            } catch(e: Exception) {
                deleteObjectQuietly(objectKey)
                throw e
            }

            logger.info("\n\t[INFO] [uploaded_file_service][upload_file] File uploaded\n\townerType={}\n\townerId={}\n\tfileId={}", ownerType, ownerId, saved.id)
            return UploadedFileDTO.from(saved)
        } catch(e: InvalidFileTypeException) {
            logger.warn("\n\t[WARN] [uploaded_file_service][upload_file] Rejected file\n\townerType={}\n\townerId={}\n\treason={}", ownerType, ownerId, e.message)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [uploaded_file_service][upload_file] Error uploading file\n\townerType={}\n\townerId={}\n\treason={}", ownerType, ownerId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun listFiles(ownerType: FileOwnerType, ownerId: UUID): List<UploadedFileDTO> {
        logger.debug("\n\t[DEBUG] [uploaded_file_service][list_files] Listing files\n\townerType={}\n\townerId={}", ownerType, ownerId)

        try {
            val files = uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType, ownerId).map { UploadedFileDTO.from(it) }

            logger.info("\n\t[INFO] [uploaded_file_service][list_files] Retrieved files\n\townerType={}\n\townerId={}\n\tcount={}", ownerType, ownerId, files.size)
            return files
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [uploaded_file_service][list_files] Error listing files\n\townerType={}\n\townerId={}\n\treason={}", ownerType, ownerId, e.message)
            throw e
        }
    }

    fun loadFileResource(ownerType: FileOwnerType, fileId: UUID, ownerId: UUID? = null): Pair<Resource, UploadedFile> {
        logger.debug("\n\t[DEBUG] [uploaded_file_service][load_file_resource] Loading file resource\n\townerType={}\n\townerId={}\n\tfileId={}", ownerType, ownerId, fileId)

        try {
            val uploadedFile = (if(ownerId != null) uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, ownerType, ownerId) else uploadedFileRepository.findByIdAndOwnerType(fileId, ownerType))
                .orElseThrow { UploadedFileNotFoundException(fileId) }

            return InputStreamResource(storageService.getObject(uploadedFile.objectKey)) to uploadedFile
        } catch(e: UploadedFileNotFoundException) {
            logger.warn("\n\t[WARN] [uploaded_file_service][load_file_resource] File {} not found", fileId)
            throw e
        } catch(e: StorageNotFoundException) {
            logger.warn("\n\t[WARN] [uploaded_file_service][load_file_resource] File {} missing from storage", fileId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [uploaded_file_service][load_file_resource] Error loading file\n\townerType={}\n\tfileId={}\n\treason={}", ownerType, fileId, e.message)
            throw e
        }
    }

    @Transactional
    fun deleteFile(ownerType: FileOwnerType, ownerId: UUID, fileId: UUID) {
        logger.debug("\n\t[DEBUG] [uploaded_file_service][delete_file] Deleting file\n\townerType={}\n\townerId={}\n\tfileId={}", ownerType, ownerId, fileId)

        try {
            val uploadedFile = uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, ownerType, ownerId)
                .orElseThrow { UploadedFileNotFoundException(fileId) }

            val objectKey = uploadedFile.objectKey
            uploadedFileRepository.delete(uploadedFile)

            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = deleteObjectQuietly(objectKey)
            })

            logger.info("\n\t[INFO] [uploaded_file_service][delete_file] File deleted\n\townerType={}\n\townerId={}\n\tfileId={}", ownerType, ownerId, fileId)
        } catch(e: UploadedFileNotFoundException) {
            logger.warn("\n\t[WARN] [uploaded_file_service][delete_file] File {} not found", fileId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [uploaded_file_service][delete_file] Error deleting file\n\townerType={}\n\townerId={}\n\tfileId={}\n\treason={}", ownerType, ownerId, fileId, e.message)
            throw e
        }
    }

    private fun buildObjectKey(ownerType: FileOwnerType, ownerId: UUID, fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").takeIf { EXTENSION_REGEX.matches(it) }?.lowercase()
        return "${ownerType.name.lowercase()}/$ownerId/${UUID.randomUUID()}${extension?.let { ".$it" }.orEmpty()}"
    }

    private fun deleteObjectQuietly(objectKey: String) {
        try {
            storageService.deleteObject(objectKey)
        } catch(e: Exception) {
            logger.warn("\n\t[WARN] [uploaded_file_service][delete_object_quietly] Could not delete object from storage\n\tobjectKey={}\n\treason={}", objectKey, e.message)
        }
    }
}