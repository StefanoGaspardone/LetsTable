package com.backend.unit.services

import com.backend.exceptions.StorageNotFoundException
import com.backend.exceptions.StorageReadException
import com.backend.exceptions.StorageWriteException
import com.backend.properties.S3Properties
import com.backend.services.StorageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.ByteArrayInputStream

class StorageServiceTest {

    private lateinit var s3Client: S3Client
    private lateinit var service: StorageService

    private val putRequestSlot = slot<PutObjectRequest>()
    private val bodySlot = slot<RequestBody>()
    private val getRequestSlot = slot<GetObjectRequest>()
    private val deleteRequestSlot = slot<DeleteObjectRequest>()

    companion object {
        private const val BUCKET = "letstable-test"
        private const val OBJECT_KEY = "avatars/user-123.webp"
        private const val CONTENT_TYPE = "image/webp"
        private val CONTENT = "fake image bytes".toByteArray()
    }

    @BeforeEach
    fun setUp() {
        s3Client = mockk()
        val properties = mockk<S3Properties> { every { bucket } returns BUCKET }

        every { s3Client.putObject(capture(putRequestSlot), capture(bodySlot)) } returns PutObjectResponse.builder().build()
        every { s3Client.deleteObject(capture(deleteRequestSlot)) } returns DeleteObjectResponse.builder().build()

        service = StorageService(s3Client, properties)
    }

    private fun s3Error(status: Int): S3Exception = S3Exception.builder().statusCode(status).message("HTTP $status").build() as S3Exception

    @Nested
    @DisplayName("putObject")
    inner class PutObjectTests {

        @Test
        fun `should upload to the configured bucket with the key and the content type`() {
            service.putObject(OBJECT_KEY, ByteArrayInputStream(CONTENT), CONTENT.size.toLong(), CONTENT_TYPE)

            val request = putRequestSlot.captured
            assertThat(request.bucket()).isEqualTo(BUCKET)
            assertThat(request.key()).isEqualTo(OBJECT_KEY)
            assertThat(request.contentType()).isEqualTo(CONTENT_TYPE)
        }

        @Test
        fun `should upload the whole stream with the declared size`() {
            service.putObject(OBJECT_KEY, ByteArrayInputStream(CONTENT), CONTENT.size.toLong(), CONTENT_TYPE)

            val body = bodySlot.captured
            assertThat(body.optionalContentLength()).hasValue(CONTENT.size.toLong())
            assertThat(body.contentStreamProvider().newStream().readAllBytes()).isEqualTo(CONTENT)
        }

        @Test
        fun `should throw StorageWriteException when the storage rejects the upload`() {
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } throws s3Error(403)

            assertThatThrownBy { service.putObject(OBJECT_KEY, ByteArrayInputStream(CONTENT), CONTENT.size.toLong(), CONTENT_TYPE) }
                .isInstanceOf(StorageWriteException::class.java)
        }

        @Test
        fun `should throw StorageWriteException when the storage cannot be reached`() {
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } throws SdkClientException.create("Unable to connect")

            assertThatThrownBy { service.putObject(OBJECT_KEY, ByteArrayInputStream(CONTENT), CONTENT.size.toLong(), CONTENT_TYPE) }
                .isInstanceOf(StorageWriteException::class.java)
        }
    }

    @Nested
    @DisplayName("getObject")
    inner class GetObjectTests {

        @Test
        fun `should return the object stream from the configured bucket`() {
            every { s3Client.getObject(capture(getRequestSlot)) } returns ResponseInputStream(GetObjectResponse.builder().build(), ByteArrayInputStream(CONTENT))

            val result = service.getObject(OBJECT_KEY)

            assertThat(result.readAllBytes()).isEqualTo(CONTENT)
            assertThat(getRequestSlot.captured.bucket()).isEqualTo(BUCKET)
            assertThat(getRequestSlot.captured.key()).isEqualTo(OBJECT_KEY)
        }

        @Test
        fun `should throw StorageNotFoundException when the key does not exist`() {
            every { s3Client.getObject(any<GetObjectRequest>()) } throws NoSuchKeyException.builder().message("The specified key does not exist").build()

            assertThatThrownBy { service.getObject(OBJECT_KEY) }
                .isInstanceOf(StorageNotFoundException::class.java)
        }

        @Test
        fun `should throw StorageNotFoundException when the storage answers 404 without a NoSuchKey code`() {
            every { s3Client.getObject(any<GetObjectRequest>()) } throws s3Error(404)

            assertThatThrownBy { service.getObject(OBJECT_KEY) }
                .isInstanceOf(StorageNotFoundException::class.java)
        }

        @ParameterizedTest
        @ValueSource(ints = [400, 403, 500, 503])
        fun `should throw StorageReadException for other storage errors`(status: Int) {
            every { s3Client.getObject(any<GetObjectRequest>()) } throws s3Error(status)

            assertThatThrownBy { service.getObject(OBJECT_KEY) }
                .isInstanceOf(StorageReadException::class.java)
        }

        @Test
        fun `should throw StorageReadException when the storage cannot be reached`() {
            every { s3Client.getObject(any<GetObjectRequest>()) } throws SdkClientException.create("Unable to connect")

            assertThatThrownBy { service.getObject(OBJECT_KEY) }
                .isInstanceOf(StorageReadException::class.java)
        }
    }

    @Nested
    @DisplayName("deleteObject")
    inner class DeleteObjectTests {

        @Test
        fun `should delete the key from the configured bucket`() {
            service.deleteObject(OBJECT_KEY)

            assertThat(deleteRequestSlot.captured.bucket()).isEqualTo(BUCKET)
            assertThat(deleteRequestSlot.captured.key()).isEqualTo(OBJECT_KEY)
            verify(exactly = 1) { s3Client.deleteObject(any<DeleteObjectRequest>()) }
        }

        @Test
        fun `should throw StorageWriteException when the deletion fails`() {
            every { s3Client.deleteObject(any<DeleteObjectRequest>()) } throws s3Error(500)

            assertThatThrownBy { service.deleteObject(OBJECT_KEY) }
                .isInstanceOf(StorageWriteException::class.java)
        }
    }
}