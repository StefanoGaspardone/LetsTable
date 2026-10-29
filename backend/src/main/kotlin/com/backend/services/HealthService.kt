package com.backend.services

import com.backend.models.dtos.HealthResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.availability.ApplicationAvailability
import org.springframework.boot.availability.ReadinessState
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.stereotype.Service

@Service
class HealthService(
    private val jdbcTemplate: JdbcTemplate,
    private val applicationAvailability: ApplicationAvailability,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val UP = "UP"
        private const val DOWN = "DOWN"
        private const val STARTING = "STARTING"
        private const val UNKNOWN = "UNKNOWN"
    }

    fun checkHealth(): HealthResponse {
        if(applicationAvailability.readinessState != ReadinessState.ACCEPTING_TRAFFIC) {
            return HealthResponse(status = STARTING, database = UNKNOWN)
        }

        val databaseUp = try {
            jdbcTemplate.queryForObject<Int>("SELECT 1") == 1
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [health_service][check_health] Database unreachable: {}", e.message)
            false
        }

        val status = if(databaseUp) UP else DOWN
        return HealthResponse(status = status, database = status)
    }
}