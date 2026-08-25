package com.backend.services

import com.backend.exceptions.InvalidFileTypeException
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
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class UploadedFileService(
    private val uploadedFileRepository: UploadedFileRepository,
    private val userRepository: UserRepository,
    private val storageService: StorageService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun uploadFile(
        ownerType: FileOwnerType,
        ownerId: UUID,
        file: MultipartFile,
        allowedContentTypes: Set<String>,
    ): UploadedFileDTO {
        logger.debug("\n\t[DEBUG] [uploaded_file_service][upload_file] Uploading file\n\townerType={}\n\townerId={}\n\tfileName={}", ownerType, ownerId, file.originalFilename)

        try {
            if(file.contentType !in allowedContentTypes) throw InvalidFileTypeException("Unsupported file type: ${file.contentType}")

            val currentUser = userRepository.findById(CurrentUser.id()).orElse(null)
            val extension = file.originalFilename?.substringAfterLast('.', "") ?: ""
            val objectKey = "${ownerType.name.lowercase()}/$ownerId/${UUID.randomUUID()}${if (extension.isNotBlank()) ".$extension" else ""}"

            file.inputStream.use { stream ->
                storageService.putObject(objectKey, stream, file.size, file.contentType ?: "application/octet-stream")
            }

            val uploadedFile = uploadedFileRepository.save(
                UploadedFile(
                    ownerType = ownerType,
                    ownerId = ownerId,
                    uploadedBy = currentUser,
                    fileName = file.originalFilename ?: "file",
                    objectKey = objectKey,
                    contentType = file.contentType ?: "application/octet-stream",
                    size = file.size,
                )
            )

            logger.info("\n\t[INFO] [uploaded_file_service][upload_file] File uploaded\n\townerType={}\n\townerId={}\n\tfileId={}", ownerType, ownerId, uploadedFile.id)
            return UploadedFileDTO.from(uploadedFile)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [uploaded_file_service][upload_file] Error uploading file\n\townerType={}\n\townerId={}\n\treason={}", ownerType, ownerId, e.message)
            throw e
        }
    }

    @Transactional
    fun listFiles(ownerType: FileOwnerType, ownerId: UUID): List<UploadedFileDTO> {
        logger.debug("\n\t[DEBUG] [uploaded_file_service][list_files] Listing files\n\townerType={}\n\townerId={}", ownerType, ownerId)

        try {
            val files = uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType, ownerId)

            logger.info("\n\t[INFO] [uploaded_file_service][list_files] Retrieved files\n\townerType={}\n\townerId={}\n\tcount={}", ownerType, ownerId, files.size)
            return files.map { UploadedFileDTO.from(it) }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [uploaded_file_service][list_files] Error listing files\n\townerType={}\n\townerId={}\n\treason={}", ownerType, ownerId, e.message)
            throw e
        }
    }

    @Transactional
    fun loadFileResource(ownerType: FileOwnerType, ownerId: UUID, fileId: UUID): Pair<Resource, UploadedFile> {
        logger.debug("\n\t[DEBUG] [uploaded_file_service][load_file_resource] Loading file resource\n\townerType={}\n\townerId={}\n\tfileId={}", ownerType, ownerId, fileId)

        try {
            val uploadedFile = uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, ownerType, ownerId)
                .orElseThrow { UploadedFileNotFoundException(fileId) }

            val stream = storageService.getObject(uploadedFile.objectKey)
            return InputStreamResource(stream) to uploadedFile
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [uploaded_file_service][load_file_resource] Error loading file\n\townerType={}\n\townerId={}\n\tfileId={}\n\treason={}", ownerType, ownerId, fileId, e.message)
            throw e
        }
    }

    @Transactional
    fun deleteFile(ownerType: FileOwnerType, ownerId: UUID, fileId: UUID) {
        logger.debug("\n\t[DEBUG] [uploaded_file_service][delete_file] Deleting file\n\townerType={}\n\townerId={}\n\tfileId={}", ownerType, ownerId, fileId)

        try {
            val uploadedFile = uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, ownerType, ownerId)
                .orElseThrow { UploadedFileNotFoundException(fileId) }

            storageService.deleteObject(uploadedFile.objectKey)
            uploadedFileRepository.delete(uploadedFile)

            logger.info("\n\t[INFO] [uploaded_file_service][delete_file] File deleted\n\townerType={}\n\townerId={}\n\tfileId={}", ownerType, ownerId, fileId)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [uploaded_file_service][delete_file] Error deleting file\n\townerType={}\n\townerId={}\n\tfileId={}\n\treason={}", ownerType, ownerId, fileId, e.message)
            throw e
        }
    }
}