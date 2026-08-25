package com.backend.services

import com.backend.exceptions.StorageNotFoundException
import com.backend.exceptions.StorageWriteException
import com.backend.properties.MinIOProperties
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class StorageService(
    private val minioClient: MinioClient,
    private val properties: MinIOProperties,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun putObject(objectKey: String, input: InputStream, size: Long, contentType: String) {
        logger.debug("\n\t[DEBUG] [storage_service][put_object] Starting storage\n\tobjectKey={}\n\tsize={}", objectKey, size)
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.bucket)
                    .`object`(objectKey)
                    .stream(input, size, -1)
                    .contentType(contentType)
                    .build()
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
            val result = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(properties.bucket)
                    .`object`(objectKey)
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
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(properties.bucket)
                    .`object`(objectKey)
                    .build()
            )
            logger.info("\n\t[INFO] [storage_service][delete_object] Successful deletion\n\tobjectKey={}", objectKey)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [storage_service][delete_object] Failed deletion\n\tobjectKey={}", objectKey, e)
            throw StorageWriteException(objectKey)
        }
    }
}