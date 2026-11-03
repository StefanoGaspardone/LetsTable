package com.backend.repositories

import com.backend.models.entities.AppRelease
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface AppReleaseRepository: JpaRepository<AppRelease, UUID> {

    fun findFirstByOrderByCreatedAtDesc(): Optional<AppRelease>

    fun existsByVersion(version: String): Boolean
}