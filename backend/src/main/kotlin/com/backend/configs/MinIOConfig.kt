package com.backend.configs

import com.backend.properties.MinIOProperties
import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MinIOConfig(private val properties: MinIOProperties) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun minioClient(): MinioClient {
        val client = MinioClient.builder()
            .endpoint(properties.url)
            .credentials(properties.accessKey, properties.secretKey)
            .build()

        ensureBucketExists(client)
        return client
    }

    private fun ensureBucketExists(client: MinioClient) {
        try {
            val exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket).build())
            if(!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket).build())
                logger.info("\n\t[INFO] [minio_config][ensure_bucket_exists] Created MinIO bucket\n\tbucket={}", properties.bucket)
            }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [minio_config][ensure_bucket_exists] Error ensuring MinIO bucket exists: {}", e.message)
            throw e
        }
    }
}