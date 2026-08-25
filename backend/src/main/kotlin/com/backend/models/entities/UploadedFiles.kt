package com.backend.models.entities

import com.backend.models.enums.FileOwnerType
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "uploaded_files")
class UploadedFile(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    var ownerType: FileOwnerType,

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id", nullable = true)
    var uploadedBy: User? = null,

    @Column(name = "file_name", nullable = false)
    var fileName: String,

    @Column(name = "object_key", nullable = false, unique = true)
    var objectKey: String,

    @Column(name = "content_type", nullable = false)
    var contentType: String,

    @Column(name = "size", nullable = false)
    var size: Long,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)