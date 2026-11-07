package com.backend.unit.services

import com.backend.exceptions.InvalidFileTypeException
import com.backend.exceptions.StorageNotFoundException
import com.backend.exceptions.StorageWriteException
import com.backend.exceptions.UploadedFileNotFoundException
import com.backend.models.entities.UploadedFile
import com.backend.models.entities.User
import com.backend.models.enums.FileOwnerType
import com.backend.repositories.UploadedFileRepository
import com.backend.repositories.UserRepository
import com.backend.security.CurrentUser
import com.backend.services.StorageService
import com.backend.services.UploadedFileService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.InputStream
import java.util.Optional
import java.util.UUID

class UploadedFileServiceTest {

    private lateinit var uploadedFileRepository: UploadedFileRepository
    private lateinit var userRepository: UserRepository
    private lateinit var storageService: StorageService
    private lateinit var service: UploadedFileService

    private val currentUserId = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val fileId = UUID.randomUUID()
    private val user = User(id = currentUserId, username = "stefano", email = "stefano@example.com", passwordHash = "hash")

    private val savedSlot = slot<UploadedFile>()
    private val keySlot = slot<String>()
    private var uploadedBytes: ByteArray? = null

    companion object {
        private val OWNER_TYPE = FileOwnerType.GAME_RULE
        private const val PDF = "application/pdf"
        private val CONTENT = "pdf contents".toByteArray()
        private val ALLOWED: (String?) -> Boolean = { it == PDF }
        private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    }

    @BeforeEach
    fun setUp() {
        uploadedFileRepository = mockk(relaxUnitFun = true)
        userRepository = mockk()
        storageService = mockk(relaxUnitFun = true)

        mockkObject(CurrentUser)
        every { CurrentUser.id() } returns currentUserId

        uploadedBytes = null
        every { userRepository.findById(currentUserId) } returns Optional.of(user)
        every { storageService.putObject(capture(keySlot), any(), any(), any()) } answers { uploadedBytes = secondArg<InputStream>().readAllBytes() }
        every { uploadedFileRepository.save(capture(savedSlot)) } answers { firstArg<UploadedFile>().apply { id = fileId } }

        service = UploadedFileService(uploadedFileRepository, userRepository, storageService)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CurrentUser)
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun multipart(name: String? = "Dune-Imperium-Rulebook.pdf", contentType: String? = PDF, content: ByteArray = CONTENT) =
        MockMultipartFile("file", name, contentType, content)

    private fun upload(file: MockMultipartFile = multipart(), isAllowed: (String?) -> Boolean = ALLOWED) =
        service.uploadFile(OWNER_TYPE, ownerId, file, isAllowed)

    private fun storedFile(ownerType: FileOwnerType = OWNER_TYPE, id: UUID = fileId, fileName: String = "rules.pdf") = UploadedFile(
        id = id,
        ownerType = ownerType,
        ownerId = ownerId,
        uploadedBy = user,
        fileName = fileName,
        objectKey = "${ownerType.name.lowercase()}/$ownerId/key.pdf",
        contentType = PDF,
        size = CONTENT.size.toLong(),
    )

    @Nested
    @DisplayName("uploadFile")
    inner class UploadFileTests {

        @Test
        fun `should store the file and save its metadata`() {
            val result = upload()

            val saved = savedSlot.captured
            assertThat(saved.ownerType).isEqualTo(OWNER_TYPE)
            assertThat(saved.ownerId).isEqualTo(ownerId)
            assertThat(saved.uploadedBy).isSameAs(user)
            assertThat(saved.fileName).isEqualTo("Dune-Imperium-Rulebook.pdf")
            assertThat(saved.contentType).isEqualTo(PDF)
            assertThat(saved.size).isEqualTo(CONTENT.size.toLong())
            assertThat(saved.objectKey).isEqualTo(keySlot.captured)
            assertThat(result.id).isEqualTo(fileId)
            assertThat(result.fileName).isEqualTo("Dune-Imperium-Rulebook.pdf")
            assertThat(result.uploadedByUsername).isEqualTo("stefano")
        }

        @Test
        fun `should send the whole content to the storage with its size and type`() {
            upload()

            assertThat(uploadedBytes).isEqualTo(CONTENT)
            verify(exactly = 1) { storageService.putObject(any(), any(), CONTENT.size.toLong(), PDF) }
        }

        @Test
        fun `should store the file before saving the metadata`() {
            upload()

            verifyOrder {
                storageService.putObject(any(), any(), any(), any())
                uploadedFileRepository.save(any<UploadedFile>())
            }
        }

        @Test
        fun `should build the key from owner type, owner id and a random name`() {
            upload()

            assertThat(keySlot.captured).matches("game_rule/$ownerId/$UUID_PATTERN\\.pdf")
        }

        @Test
        fun `should use a different key for every upload of the same file`() {
            upload()
            val firstKey = keySlot.captured
            upload()

            assertThat(keySlot.captured).isNotEqualTo(firstKey)
        }

        @ParameterizedTest
        @CsvSource(
            "Rules.PDF, .pdf",
            "archive.tar.gz, .gz",
            "README, ''",
            "notes., ''",
            "file.verylongextension, ''",
            "weird.p-d-f, ''",
        )
        fun `should keep only a safe lowercase extension in the key`(name: String, expectedSuffix: String) {
            upload(multipart(name = name))

            assertThat(keySlot.captured).matches("game_rule/$ownerId/$UUID_PATTERN${Regex.escape(expectedSuffix)}")
        }

        @Test
        fun `should trim the file name`() {
            upload(multipart(name = "  rules.pdf  "))

            assertThat(savedSlot.captured.fileName).isEqualTo("rules.pdf")
        }

        @Test
        fun `should use a default name when the original one is blank`() {
            upload(multipart(name = "   "))

            assertThat(savedSlot.captured.fileName).isEqualTo("file")
        }

        @Test
        fun `should use a default name when the original one is missing`() {
            upload(multipart(name = null))

            assertThat(savedSlot.captured.fileName).isEqualTo("file")
        }

        @Test
        fun `should truncate a file name longer than 255 characters`() {
            upload(multipart(name = "a".repeat(300) + ".pdf"))

            assertThat(savedSlot.captured.fileName).hasSize(255)
        }

        @ParameterizedTest
        @CsvSource(
            "'C:\\fakepath\\rules.pdf', rules.pdf",
            "../../etc/rules.pdf, rules.pdf",
            "folder/sub/rules.pdf, rules.pdf",
        )
        fun `should strip any path from the file name`(name: String, expected: String) {
            upload(multipart(name = name))

            assertThat(savedSlot.captured.fileName).isEqualTo(expected)
        }

        @Test
        fun `should use a default content type when the file has none and it is allowed`() {
            upload(multipart(contentType = null), isAllowed = { true })

            assertThat(savedSlot.captured.contentType).isEqualTo("application/octet-stream")
            verify(exactly = 1) { storageService.putObject(any(), any(), any(), "application/octet-stream") }
        }

        @Test
        fun `should save the file without uploader when the user cannot be found`() {
            every { userRepository.findById(currentUserId) } returns Optional.empty()

            val result = upload()

            assertThat(savedSlot.captured.uploadedBy).isNull()
            assertThat(result.uploadedByUsername).isNull()
        }

        @Test
        fun `should reject an empty file`() {
            assertThatThrownBy { upload(multipart(content = ByteArray(0))) }
                .isInstanceOf(InvalidFileTypeException::class.java)
                .hasMessageContaining("empty")

            verify(exactly = 0) { storageService.putObject(any(), any(), any(), any()) }
            verify(exactly = 0) { uploadedFileRepository.save(any<UploadedFile>()) }
        }

        @Test
        fun `should reject a content type that is not allowed`() {
            var checkedType: String? = null

            assertThatThrownBy { upload(multipart(name = "script.sh", contentType = "text/x-shellscript")) { checkedType = it; false } }
                .isInstanceOf(InvalidFileTypeException::class.java)
                .hasMessageContaining("text/x-shellscript")

            assertThat(checkedType).isEqualTo("text/x-shellscript")
            verify(exactly = 0) { storageService.putObject(any(), any(), any(), any()) }
            verify(exactly = 0) { uploadedFileRepository.save(any<UploadedFile>()) }
        }

        @Test
        fun `should not save the metadata when the storage fails`() {
            every { storageService.putObject(any(), any(), any(), any()) } throws StorageWriteException("key")

            assertThatThrownBy { upload() }.isInstanceOf(StorageWriteException::class.java)

            verify(exactly = 0) { uploadedFileRepository.save(any<UploadedFile>()) }
            verify(exactly = 0) { storageService.deleteObject(any()) }
        }

        @Test
        fun `should remove the stored object when saving the metadata fails`() {
            every { uploadedFileRepository.save(any<UploadedFile>()) } throws RuntimeException("Database error")

            assertThatThrownBy { upload() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Database error")

            verify(exactly = 1) { storageService.deleteObject(keySlot.captured) }
        }

        @Test
        fun `should keep the original error when the cleanup fails too`() {
            every { uploadedFileRepository.save(any<UploadedFile>()) } throws RuntimeException("Database error")
            every { storageService.deleteObject(any()) } throws StorageWriteException("key")

            assertThatThrownBy { upload() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Database error")
        }
    }

    @Nested
    @DisplayName("listFiles")
    inner class ListFilesTests {

        @Test
        fun `should map the files keeping the repository order`() {
            val newer = storedFile(id = UUID.randomUUID(), fileName = "Ark-Nova-Rules.pdf")
            val older = storedFile(id = UUID.randomUUID(), fileName = "Dune-Imperium-Rulebook.pdf")
            every { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(OWNER_TYPE, ownerId) } returns listOf(newer, older)

            val result = service.listFiles(OWNER_TYPE, ownerId)

            assertThat(result.map { it.fileName }).containsExactly("Ark-Nova-Rules.pdf", "Dune-Imperium-Rulebook.pdf")
            assertThat(result).allMatch { it.uploadedByUsername == "stefano" }
        }

        @Test
        fun `should return an empty list when there are no files`() {
            every { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(OWNER_TYPE, ownerId) } returns emptyList()

            assertThat(service.listFiles(OWNER_TYPE, ownerId)).isEmpty()
        }

        @Test
        fun `should propagate a repository failure`() {
            every { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(OWNER_TYPE, ownerId) } throws RuntimeException("Database error")

            assertThatThrownBy { service.listFiles(OWNER_TYPE, ownerId) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }

    @Nested
    @DisplayName("loadFileResource")
    inner class LoadFileResourceTests {

        @Test
        fun `should load the file by id and type when no owner is given`() {
            val file = storedFile(FileOwnerType.USER_AVATAR)
            every { uploadedFileRepository.findByIdAndOwnerType(fileId, FileOwnerType.USER_AVATAR) } returns Optional.of(file)
            every { storageService.getObject(file.objectKey) } returns CONTENT.inputStream()

            val (resource, entity) = service.loadFileResource(FileOwnerType.USER_AVATAR, fileId)

            assertThat(entity).isSameAs(file)
            assertThat(resource.inputStream.readAllBytes()).isEqualTo(CONTENT)
            verify(exactly = 0) { uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(any(), any(), any()) }
        }

        @Test
        fun `should restrict the lookup to the owner when one is given`() {
            val file = storedFile()
            every { uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, OWNER_TYPE, ownerId) } returns Optional.of(file)
            every { storageService.getObject(file.objectKey) } returns CONTENT.inputStream()

            val (_, entity) = service.loadFileResource(OWNER_TYPE, fileId, ownerId)

            assertThat(entity).isSameAs(file)
            verify(exactly = 0) { uploadedFileRepository.findByIdAndOwnerType(any(), any()) }
        }

        @Test
        fun `should throw UploadedFileNotFoundException without reading the storage`() {
            every { uploadedFileRepository.findByIdAndOwnerType(fileId, OWNER_TYPE) } returns Optional.empty()

            assertThatThrownBy { service.loadFileResource(OWNER_TYPE, fileId) }
                .isInstanceOf(UploadedFileNotFoundException::class.java)

            verify(exactly = 0) { storageService.getObject(any()) }
        }

        @Test
        fun `should throw UploadedFileNotFoundException when the file belongs to another owner`() {
            every { uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, OWNER_TYPE, ownerId) } returns Optional.empty()

            assertThatThrownBy { service.loadFileResource(OWNER_TYPE, fileId, ownerId) }
                .isInstanceOf(UploadedFileNotFoundException::class.java)
        }

        @Test
        fun `should propagate a file missing from the storage`() {
            val file = storedFile()
            every { uploadedFileRepository.findByIdAndOwnerType(fileId, OWNER_TYPE) } returns Optional.of(file)
            every { storageService.getObject(file.objectKey) } throws StorageNotFoundException(file.objectKey)

            assertThatThrownBy { service.loadFileResource(OWNER_TYPE, fileId) }
                .isInstanceOf(StorageNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("deleteFile")
    inner class DeleteFileTests {

        private lateinit var file: UploadedFile

        @BeforeEach
        fun startTransaction() {
            TransactionSynchronizationManager.initSynchronization()
            file = storedFile()
            every { uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, OWNER_TYPE, ownerId) } returns Optional.of(file)
        }

        private fun commit() = TransactionSynchronizationManager.getSynchronizations().forEach {
            it.afterCommit()
            it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
        }

        private fun rollback() = TransactionSynchronizationManager.getSynchronizations().forEach {
            it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        }

        @Test
        fun `should delete the metadata right away and the object only after the commit`() {
            service.deleteFile(OWNER_TYPE, ownerId, fileId)

            verify(exactly = 1) { uploadedFileRepository.delete(file) }
            verify(exactly = 0) { storageService.deleteObject(any()) }

            commit()

            verify(exactly = 1) { storageService.deleteObject(file.objectKey) }
        }

        @Test
        fun `should keep the object when the transaction rolls back`() {
            service.deleteFile(OWNER_TYPE, ownerId, fileId)
            rollback()

            verify(exactly = 0) { storageService.deleteObject(any()) }
        }

        @Test
        fun `should not fail the commit when the object cannot be deleted`() {
            every { storageService.deleteObject(any()) } throws StorageWriteException(file.objectKey)

            service.deleteFile(OWNER_TYPE, ownerId, fileId)

            assertThatCode { commit() }.doesNotThrowAnyException()
        }

        @Test
        fun `should throw UploadedFileNotFoundException when the file does not belong to the owner`() {
            every { uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(fileId, OWNER_TYPE, ownerId) } returns Optional.empty()

            assertThatThrownBy { service.deleteFile(OWNER_TYPE, ownerId, fileId) }
                .isInstanceOf(UploadedFileNotFoundException::class.java)

            verify(exactly = 0) { uploadedFileRepository.delete(any<UploadedFile>()) }
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
        }

        @Test
        fun `should not schedule the object deletion when deleting the metadata fails`() {
            every { uploadedFileRepository.delete(file) } throws RuntimeException("Database error")

            assertThatThrownBy { service.deleteFile(OWNER_TYPE, ownerId, fileId) }
                .isInstanceOf(RuntimeException::class.java)

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
        }
    }
}