package com.backend.integration.controllers

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        private val minio: GenericContainer<*> = GenericContainer(DockerImageName.parse("minio/minio:RELEASE.2024-01-16T16-07-38Z"))
            .withEnv("MINIO_ROOT_USER", "test-key")
            .withEnv("MINIO_ROOT_PASSWORD", "test-secret")
            .withCommand("server /data")
            .withExposedPorts(9000)

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

            registry.add("minio.url") { "http://${minio.host}:${minio.getMappedPort(9000)}" }
            registry.add("minio.access-key") { "test-key" }
            registry.add("minio.secret-key") { "test-secret" }
        }
    }
}