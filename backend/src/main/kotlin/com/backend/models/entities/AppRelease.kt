package com.backend.models.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "app_releases")
class AppRelease(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "version", nullable = false, unique = true, length = 20)
    var version: String,

    @Column(name = "min_supported_version", nullable = false, length = 20)
    var minSupportedVersion: String,

    @Column(name = "download_url", nullable = false, length = 500)
    var downloadUrl: String,

    @Column(name = "release_notes", columnDefinition = "TEXT")
    var releaseNotes: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)