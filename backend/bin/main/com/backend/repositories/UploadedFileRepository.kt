package com.backend.repositories

import com.backend.models.entities.UploadedFile
import com.backend.models.enums.FileOwnerType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UploadedFileRepository: JpaRepository<UploadedFile, UUID> {

    fun findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType: FileOwnerType, ownerId: UUID): List<UploadedFile>

    fun findByIdAndOwnerTypeAndOwnerId(id: UUID, ownerType: FileOwnerType, ownerId: UUID): Optional<UploadedFile>
}