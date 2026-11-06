package com.backend.repositories

import com.backend.models.entities.UploadedFile
import com.backend.models.enums.FileOwnerType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UploadedFileRepository: JpaRepository<UploadedFile, UUID> {

    @EntityGraph(attributePaths = ["uploadedBy"])
    fun findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType: FileOwnerType, ownerId: UUID): List<UploadedFile>

    fun findByIdAndOwnerType(id: UUID, ownerType: FileOwnerType): Optional<UploadedFile>

    fun findByIdAndOwnerTypeAndOwnerId(id: UUID, ownerType: FileOwnerType, ownerId: UUID): Optional<UploadedFile>

    @Modifying
    @Query("UPDATE UploadedFile f SET f.uploadedBy = NULL WHERE f.uploadedBy.id = :userId")
    fun clearUploadedByUser(@Param("userId") userId: UUID): Int
}