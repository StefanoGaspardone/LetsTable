package com.backend.unit.services

import com.backend.exceptions.StorageNotFoundException
import com.backend.exceptions.StorageWriteException
import com.backend.properties.S3Properties
import com.backend.services.StorageService
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
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.ByteArrayInputStream

@ExtendWith(MockitoExtension::class)
class StorageServiceTest {

    @Mock
    private lateinit var s3Client: S3Client

    @Mock
    private lateinit var properties: S3Properties

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

            val requestCaptor = ArgumentCaptor.forClass(PutObjectRequest::class.java)

            storageService.putObject(objectKey, inputStream, size, contentType)

            verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody::class.java))

            val capturedRequest = requestCaptor.value
            assertThat(capturedRequest.bucket()).isEqualTo(bucketName)
            assertThat(capturedRequest.key()).isEqualTo(objectKey)
            assertThat(capturedRequest.contentType()).isEqualTo(contentType)
        }

        @Test
        fun `should throw StorageWriteException when s3Client fails during putObject`() {
            val content = "hello world".toByteArray()
            val inputStream = ByteArrayInputStream(content)
            val size = content.size.toLong()
            val contentType = "image/jpeg"

            doThrow(RuntimeException("S3 cluster unreachable"))
                .`when`(s3Client).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))

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
            @Suppress("UNCHECKED_CAST")
            val mockResponse = mock(ResponseInputStream::class.java) as ResponseInputStream<GetObjectResponse>

            val requestCaptor = ArgumentCaptor.forClass(GetObjectRequest::class.java)
            `when`(s3Client.getObject(requestCaptor.capture())).thenReturn(mockResponse)

            val resultStream = storageService.getObject(objectKey)

            assertThat(resultStream).isNotNull
            assertThat(resultStream).isEqualTo(mockResponse)

            val capturedRequest = requestCaptor.value
            assertThat(capturedRequest.bucket()).isEqualTo(bucketName)
            assertThat(capturedRequest.key()).isEqualTo(objectKey)
        }

        @Test
        fun `should throw StorageNotFoundException when s3Client fails during getObject`() {
            `when`(s3Client.getObject(any(GetObjectRequest::class.java)))
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
            val requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest::class.java)

            storageService.deleteObject(objectKey)

            verify(s3Client).deleteObject(requestCaptor.capture())

            val capturedRequest = requestCaptor.value
            assertThat(capturedRequest.bucket()).isEqualTo(bucketName)
            assertThat(capturedRequest.key()).isEqualTo(objectKey)
        }

        @Test
        fun `should throw StorageWriteException when s3Client fails during deleteObject`() {
            doThrow(RuntimeException("S3 deletion error"))
                .`when`(s3Client).deleteObject(any(DeleteObjectRequest::class.java))

            assertThatThrownBy {
                storageService.deleteObject(objectKey)
            }.isInstanceOf(StorageWriteException::class.java)
        }
    }

    private fun <T> any(type: Class<T>): T = ArgumentMatchers.any(type)
}