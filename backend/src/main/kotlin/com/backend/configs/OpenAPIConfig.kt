package com.backend.configs

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import java.net.InetAddress

@Configuration
class OpenAPIConfig(private val environment: Environment) {

    private fun apiInfo(): Info =
        Info()
            .title("Let's Table Service API")
            .version("1.0.0")
            .description("API documentation for Let's Table Service")

    @Bean
    @Profile("!prod")
    fun devOpenApiDocumentation(): OpenAPI {
        val port = environment.getProperty("server.port", "8080")
        val ip = runCatching { InetAddress.getLocalHost().hostAddress }.getOrNull()

        val openApi = OpenAPI()
            .info(apiInfo())
            .addServersItem(Server().url("http://localhost:$port").description("Let's Table Service local device"))

        ip?.let {
            openApi.addServersItem(Server().url("http://$it:$port").description("Let's Table Service local network"))
        }

        return openApi
    }

    @Bean
    @Profile("prod")
    fun prodOpenApiDocumentation(): OpenAPI =
        OpenAPI().info(apiInfo())
}