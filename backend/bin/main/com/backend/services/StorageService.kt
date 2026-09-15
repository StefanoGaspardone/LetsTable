package com.backend.services

import com.backend.exceptions.StorageNotFoundException
import com.backend.exceptions.StorageWriteException
import com.backend.properties.S3Properties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream

@Service
class StorageService(
    private val s3Client: S3Client,
    private val properties: S3Properties,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun putObject(objectKey: String, input: InputStream, size: Long, contentType: String) {
        logger.debug("\n\t[DEBUG] [storage_service][put_object] Starting storage\n\tobjectKey={}\n\tsize={}", objectKey, size)

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromInputStream(input, size)
            )
            logger.info("\n\t[INFO] [storage_service][put_object] Successful storage\n\tobjectKey={}", objectKey)
        } catch (e: Exception) {
            logger.error("\n\t[ERROR] [storage_service][put_object] Failed storage\n\tobjectKey={}", objectKey, e)
            throw StorageWriteException(objectKey)
        }
    }

    fun getObject(objectKey: String): InputStream {
        logger.debug("\n\t[DEBUG] [storage_service][get_object] Starting retrieval\n\tobjectKey={}", objectKey)

        try {
            val result = s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(objectKey)
                    .build()
            )
            logger.info("\n\t[INFO] [storage_service][get_object] Successful retrieval\n\tobjectKey={}", objectKey)
            return result
        } catch (e: Exception) {
            logger.error("\n\t[ERROR] [storage_service][get_object] Failed retrieval\n\tobjectKey={}", objectKey, e)
            throw StorageNotFoundException(objectKey)
        }
    }

    fun deleteObject(objectKey: String) {
        logger.debug("\n\t[DEBUG] [storage_service][delete_object] Starting deletion\n\tobjectKey={}", objectKey)

        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(objectKey)
                    .build()
            )
            logger.info("\n\t[INFO] [storage_service][delete_object] Successful deletion\n\tobjectKey={}", objectKey)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [storage_service][delete_object] Failed deletion\n\tobjectKey={}", objectKey, e)
            throw StorageWriteException(objectKey)
        }
    }
}