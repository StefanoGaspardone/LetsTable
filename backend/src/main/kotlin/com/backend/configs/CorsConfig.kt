package com.backend.configs

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.cors.CorsConfigurationSource
import java.net.Inet4Address
import java.net.NetworkInterface

@Configuration
class CorsConfig(
    @Value($$"${cors.allowed-origins}") private val configuredOrigins: List<String>,
    @Value($$"${spring.profiles.active}") private val springProfiles: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        val isDev = springProfiles.isBlank() || springProfiles.split(",").any { it.trim() in listOf("dev", "local") }

        val patterns = if(isDev) {
            val devPatterns = mutableListOf(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:8080",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:8080"
            )

            configuredOrigins.filter { it.isNotBlank() }.forEach { origin ->
                devPatterns.add(origin.trim())
            }

            getLocalSubnetPatterns().forEach { subnet ->
                devPatterns.add("http://$subnet.*:5173")
                devPatterns.add("http://$subnet.*:5174")
                devPatterns.add("http://$subnet.*:8080")
                devPatterns.add("http://$subnet.*:8081")
            }

            devPatterns
        } else {
            configuredOrigins.filter { it.isNotBlank() }.map { it.trim() }
        }

        logger.info("\n\t[INFO] [cors_config] Allowed origin patterns: {}", patterns)

        configuration.allowedOriginPatterns = patterns
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    private fun getLocalSubnetPatterns(): List<String> {
        val subnets = mutableSetOf<String>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()

                if (iface.isLoopback || !iface.isUp || iface.isVirtual) continue

                for (addr in iface.interfaceAddresses) {
                    val ip = addr.address

                    if (ip is Inet4Address && ip.isSiteLocalAddress) {
                        val hostAddress = ip.hostAddress
                        val subnet = hostAddress.substring(0, hostAddress.lastIndexOf('.'))

                        subnets.add(subnet)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("\n\t[WARN] [cors_config] Failed to enumerate network interfaces: {}", e.message)
        }

        return subnets.toList()
    }
}