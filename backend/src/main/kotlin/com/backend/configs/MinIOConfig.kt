package com.backend.configs

import com.backend.properties.MinIOProperties
import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.util.concurrent.TimeUnit

@Configuration
class MinIOConfig(private val properties: MinIOProperties, private val environment: Environment) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun minioClient(): MinioClient {
        val parsedUrl = properties.url.toHttpUrl()
        val basePath = parsedUrl.encodedPath.trim('/')
        val isDefaultPort = (parsedUrl.scheme == "http" && parsedUrl.port == 80) || (parsedUrl.scheme == "https" && parsedUrl.port == 443)
        val hostOnlyEndpoint = if(isDefaultPort) {
            "${parsedUrl.scheme}://${parsedUrl.host}"
        } else {
            "${parsedUrl.scheme}://${parsedUrl.host}:${parsedUrl.port}"
        }

        val httpClient = if(basePath.isNotEmpty()) {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val originalUrl = original.url

                    val newUrl = originalUrl.newBuilder()
                        .encodedPath("/$basePath${originalUrl.encodedPath}")
                        .build()

                    chain.proceed(original.newBuilder().url(newUrl).build())
                }
                .build()
        } else {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        val client = MinioClient.builder()
            .endpoint(hostOnlyEndpoint)
            .credentials(properties.accessKey, properties.secretKey)
            .region("eu-central-1")
            .httpClient(httpClient)
            .build()

        val isDev = environment.activeProfiles.isEmpty() || environment.activeProfiles.any { it in listOf("dev", "local") }
        if(isDev) {
            ensureBucketExists(client)
        } else {
            logger.info("\n\t[INFO] [minio_config] Skipping automatic bucket creation for non-dev environment")
        }

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