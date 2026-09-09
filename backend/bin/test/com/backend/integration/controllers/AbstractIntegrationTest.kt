package com.backend.integration.controllers

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.PostgreSQLContainer

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        private val minio: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z")
            .withUserName("test-key")
            .withPassword("test-secret")

        init {
            postgres.start()
            minio.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }

            registry.add("minio.url") { minio.s3URL }
            registry.add("minio.access-key") { minio.userName }
            registry.add("minio.secret-key") { minio.password }
        }
    }
}