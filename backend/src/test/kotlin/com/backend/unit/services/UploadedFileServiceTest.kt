package com.backend.unit.services

import com.backend.exceptions.InvalidFileTypeException
import com.backend.exceptions.UploadedFileNotFoundException
import com.backend.models.entities.UploadedFile
import com.backend.models.entities.User
import com.backend.models.enums.FileOwnerType
import com.backend.models.enums.UserRole
import com.backend.repositories.UploadedFileRepository
import com.backend.repositories.UserRepository
import com.backend.security.JwtUserPrincipal
import com.backend.services.StorageService
import com.backend.services.UploadedFileService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.io.InputStream
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UploadedFileServiceTest {

    @Mock
    private lateinit var uploadedFileRepository: UploadedFileRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var storageService: StorageService

    @InjectMocks
    private lateinit var uploadedFileService: UploadedFileService

    private val currentUserId: UUID = UUID.randomUUID()
    private val ownerId: UUID = UUID.randomUUID()
    private val fileId: UUID = UUID.randomUUID()
    private val ownerType = FileOwnerType.GAME_RULE

    private val sampleUser = User(
        id = currentUserId,
        username = "stefano",
        email = "stefano@example.com",
        passwordHash = "hash",
        role = UserRole.USER
    )

    private val allowedTypes = setOf("application/pdf", "image/png", "image/jpeg")

    @BeforeEach
    fun setUpSecurityContext() {
        val principal = JwtUserPrincipal(userId = currentUserId, role = "USER")
        val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    @DisplayName("uploadFile")
    inner class UploadFileTests {

        @Test
        fun `should upload rulebook file successfully when content type is allowed and extension present`() {
            val multipartFile = MockMultipartFile(
                "file",
                "Dune-Imperium-Rulebook.pdf",
                "application/pdf",
                "pdf contents".toByteArray()
            )
            val capturedEntity = ArgumentCaptor.forClass(UploadedFile::class.java)
            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(sampleUser))
            whenever(uploadedFileRepository.save(capturedEntity.capture())).thenAnswer { invocation ->
                val entity = invocation.getArgument<UploadedFile>(0)
                entity.apply { id = fileId }
            }

            val result = uploadedFileService.uploadFile(ownerType, ownerId, multipartFile, allowedTypes)

            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(fileId)
            assertThat(result.fileName).isEqualTo("Dune-Imperium-Rulebook.pdf")
            assertThat(result.uploadedByUsername).isEqualTo("stefano")

            val saved = capturedEntity.value
            assertThat(saved.ownerType).isEqualTo(FileOwnerType.GAME_RULE)
            assertThat(saved.ownerId).isEqualTo(ownerId)
            assertThat(saved.uploadedBy).isEqualTo(sampleUser)
            assertThat(saved.objectKey).startsWith("game_rule/$ownerId/")
            assertThat(saved.objectKey).endsWith(".pdf")

            verify(storageService).putObject(
                eq(saved.objectKey),
                any<InputStream>(),
                eq(multipartFile.size),
                eq("application/pdf")
            )
        }

        @Test
        fun `should upload file correctly when file name has no extension and user is absent`() {
            val multipartFile = MockMultipartFile(
                "file",
                "README",
                "application/pdf",
                "content".toByteArray()
            )
            val capturedEntity = ArgumentCaptor.forClass(UploadedFile::class.java)
            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.empty())
            whenever(uploadedFileRepository.save(capturedEntity.capture())).thenAnswer { invocation ->
                val entity = invocation.getArgument<UploadedFile>(0)
                entity.apply { id = fileId }
            }

            val result = uploadedFileService.uploadFile(ownerType, ownerId, multipartFile, allowedTypes)

            assertThat(result).isNotNull
            assertThat(result.uploadedByUsername).isNull()

            val saved = capturedEntity.value
            assertThat(saved.uploadedBy).isNull()
            assertThat(saved.fileName).isEqualTo("README")
            assertThat(saved.objectKey).doesNotContain(".")
        }

        @Test
        fun `should throw InvalidFileTypeException when content type is not allowed`() {
            val multipartFile = MockMultipartFile(
                "file",
                "script.sh",
                "text/x-shellscript",
                "echo hello".toByteArray()
            )

            assertThatThrownBy {
                uploadedFileService.uploadFile(ownerType, ownerId, multipartFile, allowedTypes)
            }.isInstanceOf(InvalidFileTypeException::class.java)

            verify(storageService, never()).putObject(
                any(),
                any<InputStream>(),
                any(),
                any()
            )
            verify(uploadedFileRepository, never()).save(any())
        }

        @Test
        fun `should rethrow exception when storageService fails during upload`() {
            val multipartFile = MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "content".toByteArray()
            )
            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(sampleUser))
            doThrow(RuntimeException("Storage unreachable"))
                .whenever(storageService).putObject(
                    any(),
                    any<InputStream>(),
                    any(),
                    any()
                )

            assertThatThrownBy {
                uploadedFileService.uploadFile(FileOwnerType.USER_AVATAR, ownerId, multipartFile, allowedTypes)
            }.isInstanceOf(RuntimeException::class.java)

            verify(uploadedFileRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("listFiles")
    inner class ListFilesTests {

        @Test
        fun `should return list of uploaded file DTOs ordered by creation date`() {
            val file1 = UploadedFile(
                id = UUID.randomUUID(),
                ownerType = FileOwnerType.GAME_RULE,
                ownerId = ownerId,
                uploadedBy = sampleUser,
                fileName = "Dune-Imperium-Rulebook.pdf",
                objectKey = "game_rule/$ownerId/key1.pdf",
                contentType = "application/pdf",
                size = 100
            )
            val file2 = UploadedFile(
                id = UUID.randomUUID(),
                ownerType = FileOwnerType.GAME_RULE,
                ownerId = ownerId,
                uploadedBy = sampleUser,
                fileName = "Ark-Nova-Rules.pdf",
                objectKey = "game_rule/$ownerId/key2.pdf",
                contentType = "application/pdf",
                size = 200
            )
            whenever(uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType, ownerId))
                .thenReturn(listOf(file1, file2))

            val result = uploadedFileService.listFiles(ownerType, ownerId)

            assertThat(result).hasSize(2)
            assertThat(result[0].fileName).isEqualTo("Dune-Imperium-Rulebook.pdf")
            assertThat(result[0].uploadedByUsername).isEqualTo("stefano")
            assertThat(result[1].fileName).isEqualTo("Ark-Nova-Rules.pdf")
            verify(uploadedFileRepository).findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType, ownerId)
        }

        @Test
        fun `should return empty list when no files found`() {
            whenever(uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType, ownerId))
                .thenReturn(emptyList())

            val result = uploadedFileService.listFiles(ownerType, ownerId)

            assertThat(result).isEmpty()
        }

        @Test
        fun `should rethrow exception when repository throws exception`() {
            whenever(uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType, ownerId))
                .thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                uploadedFileService.listFiles(ownerType, ownerId)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    @Nested
    @DisplayName("loadFileResource")
    inner class LoadFileResourceTests {

        @Test
        fun `should load resource and entity successfully when file exists`() {
            val uploadedFile = UploadedFile(
                id = fileId,
                ownerType = FileOwnerType.USER_AVATAR,
                ownerId = ownerId,
                uploadedBy = sampleUser,
                fileName = "avatar.png",
                objectKey = "user_avatar/$ownerId/key.png",
                contentType = "image/png",
                size = 500
            )
            val fakeStream: InputStream = "avatar binary stream".byteInputStream()
            whenever(uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, FileOwnerType.USER_AVATAR, ownerId))
                .thenReturn(Optional.of(uploadedFile))
            whenever(storageService.getObject(uploadedFile.objectKey)).thenReturn(fakeStream)

            val (resource, entity) = uploadedFileService.loadFileResource(FileOwnerType.USER_AVATAR, ownerId, fileId)

            assertThat(resource).isNotNull
            assertThat(entity).isEqualTo(uploadedFile)
            verify(storageService).getObject(uploadedFile.objectKey)
        }

        @Test
        fun `should throw UploadedFileNotFoundException when file record not found in repository`() {
            whenever(uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, ownerType, ownerId))
                .thenReturn(Optional.empty())

            assertThatThrownBy {
                uploadedFileService.loadFileResource(ownerType, ownerId, fileId)
            }.isInstanceOf(UploadedFileNotFoundException::class.java)

            verify(storageService, never()).getObject(any())
        }
    }

    @Nested
    @DisplayName("deleteFile")
    inner class DeleteFileTests {

        @Test
        fun `should delete file object from storage and entity from repository successfully`() {
            val uploadedFile = UploadedFile(
                id = fileId,
                ownerType = FileOwnerType.GAME_RULE,
                ownerId = ownerId,
                uploadedBy = sampleUser,
                fileName = "Dune-Imperium-Rulebook.pdf",
                objectKey = "game_rule/$ownerId/key.pdf",
                contentType = "application/pdf",
                size = 500
            )
            whenever(uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, ownerType, ownerId))
                .thenReturn(Optional.of(uploadedFile))

            uploadedFileService.deleteFile(ownerType, ownerId, fileId)

            verify(storageService).deleteObject(uploadedFile.objectKey)
            verify(uploadedFileRepository).delete(uploadedFile)
        }

        @Test
        fun `should throw UploadedFileNotFoundException when deleting non existing file`() {
            whenever(uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, ownerType, ownerId))
                .thenReturn(Optional.empty())

            assertThatThrownBy {
                uploadedFileService.deleteFile(ownerType, ownerId, fileId)
            }.isInstanceOf(UploadedFileNotFoundException::class.java)

            verify(storageService, never()).deleteObject(any())
            verify(uploadedFileRepository, never()).delete(any())
        }

        @Test
        fun `should not delete entity from repository when storage deleteObject fails`() {
            val uploadedFile = UploadedFile(
                id = fileId,
                ownerType = FileOwnerType.GAME_RULE,
                ownerId = ownerId,
                uploadedBy = sampleUser,
                fileName = "rulebook.pdf",
                objectKey = "game_rule/$ownerId/key.pdf",
                contentType = "application/pdf",
                size = 500
            )
            whenever(uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, ownerType, ownerId))
                .thenReturn(Optional.of(uploadedFile))
            doThrow(RuntimeException("Storage deletion error"))
                .whenever(storageService).deleteObject(uploadedFile.objectKey)

            assertThatThrownBy {
                uploadedFileService.deleteFile(ownerType, ownerId, fileId)
            }.isInstanceOf(RuntimeException::class.java)

            verify(uploadedFileRepository, never()).delete(any())
        }
    }
}