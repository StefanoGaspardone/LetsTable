package com.backend.configs

import com.backend.properties.S3Properties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI

@Configuration
class S3Config(private val properties: S3Properties, private val environment: Environment) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun s3Client(): S3Client {
        val client = S3Client.builder()
            .endpointOverride(URI.create(properties.url))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
                )
            )
            .region(Region.of(properties.region))
            .forcePathStyle(true)
            .build()

        val isDev = environment.activeProfiles.isEmpty() || environment.activeProfiles.any { it in listOf("dev", "local") }
        if(isDev) {
            ensureBucketExists(client)
        } else {
            logger.info("\n\t[INFO] [s3_config] Skipping automatic bucket creation for non-dev environment")
        }

        return client
    }

    private fun ensureBucketExists(client: S3Client) {
        try {
            val exists = try {
                client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket).build())
                true
            } catch(_: NoSuchBucketException) {
                false
            } catch(e: S3Exception) {
                if(e.statusCode() == 404) false else throw e
            }

            if(!exists) {
                try {
                    client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket).build())
                    logger.info("\n\t[INFO] [s3_config][ensure_bucket_exists] Created S3 bucket\n\tbucket={}", properties.bucket)
                } catch(_: BucketAlreadyOwnedByYouException) {
                    logger.info("\n\t[INFO] [s3_config][ensure_bucket_exists] Bucket already exists\n\tbucket={}", properties.bucket)
                }
            }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [s3_config][ensure_bucket_exists] Error ensuring S3 bucket exists: {}", e.message)
            throw e
        }
    }
}