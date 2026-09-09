package com.backend.unit.services

import com.backend.exceptions.StorageNotFoundException
import com.backend.exceptions.StorageWriteException
import com.backend.properties.MinIOProperties
import com.backend.services.StorageService
import io.minio.GetObjectArgs
import io.minio.GetObjectResponse
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.io.ByteArrayInputStream
import java.io.InputStream

@ExtendWith(MockitoExtension::class)
class StorageServiceTest {

    @Mock
    private lateinit var minioClient: MinioClient

    @Mock
    private lateinit var properties: MinIOProperties

    @InjectMocks
    private lateinit var storageService: StorageService

    private val bucketName = "test-bucket"
    private val objectKey = "avatars/user-123.jpg"

    @BeforeEach
    fun setUp() {
        `when`(properties.bucket).thenReturn(bucketName)
    }

    @Nested
    @DisplayName("putObject")
    inner class PutObjectTests {

        @Test
        fun `should upload object successfully`() {
            val content = "hello world".toByteArray()
            val inputStream = ByteArrayInputStream(content)
            val size = content.size.toLong()
            val contentType = "image/jpeg"

            val argsCaptor = ArgumentCaptor.forClass(PutObjectArgs::class.java)

            storageService.putObject(objectKey, inputStream, size, contentType)

            verify(minioClient).putObject(argsCaptor.capture())

            val capturedArgs = argsCaptor.value
            assertThat(capturedArgs.bucket()).isEqualTo(bucketName)
            assertThat(capturedArgs.`object`()).isEqualTo(objectKey)
            assertThat(capturedArgs.contentType()).isEqualTo(contentType)
        }

        @Test
        fun `should throw StorageWriteException when minioClient fails during putObject`() {
            val content = "hello world".toByteArray()
            val inputStream = ByteArrayInputStream(content)
            val size = content.size.toLong()
            val contentType = "image/jpeg"

            doThrow(RuntimeException("MinIO cluster unreachable"))
                .`when`(minioClient).putObject(any(PutObjectArgs::class.java))

            assertThatThrownBy {
                storageService.putObject(objectKey, inputStream, size, contentType)
            }.isInstanceOf(StorageWriteException::class.java)
        }
    }

    @Nested
    @DisplayName("getObject")
    inner class GetObjectTests {

        @Test
        fun `should retrieve object stream successfully`() {
            val mockResponse = mock(GetObjectResponse::class.java)

            val argsCaptor = ArgumentCaptor.forClass(GetObjectArgs::class.java)
            `when`(minioClient.getObject(argsCaptor.capture())).thenReturn(mockResponse)

            val resultStream: InputStream = storageService.getObject(objectKey)

            assertThat(resultStream).isNotNull
            assertThat(resultStream).isEqualTo(mockResponse)

            val capturedArgs = argsCaptor.value
            assertThat(capturedArgs.bucket()).isEqualTo(bucketName)
            assertThat(capturedArgs.`object`()).isEqualTo(objectKey)
        }

        @Test
        fun `should throw StorageNotFoundException when minioClient fails during getObject`() {
            `when`(minioClient.getObject(any(GetObjectArgs::class.java)))
                .thenThrow(RuntimeException("Object not found in bucket"))

            assertThatThrownBy {
                storageService.getObject(objectKey)
            }.isInstanceOf(StorageNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("deleteObject")
    inner class DeleteObjectTests {

        @Test
        fun `should remove object successfully`() {
            val argsCaptor = ArgumentCaptor.forClass(RemoveObjectArgs::class.java)

            storageService.deleteObject(objectKey)

            verify(minioClient).removeObject(argsCaptor.capture())

            val capturedArgs = argsCaptor.value
            assertThat(capturedArgs.bucket()).isEqualTo(bucketName)
            assertThat(capturedArgs.`object`()).isEqualTo(objectKey)
        }

        @Test
        fun `should throw StorageWriteException when minioClient fails during removeObject`() {
            doThrow(RuntimeException("MinIO deletion error"))
                .`when`(minioClient).removeObject(any(RemoveObjectArgs::class.java))

            assertThatThrownBy {
                storageService.deleteObject(objectKey)
            }.isInstanceOf(StorageWriteException::class.java)
        }
    }

    private fun <T> any(type: Class<T>): T = ArgumentMatchers.any(type)
}