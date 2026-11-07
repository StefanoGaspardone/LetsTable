package com.backend.unit.services

import com.backend.models.dtos.HealthResponse
import com.backend.services.HealthService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.availability.ApplicationAvailability
import org.springframework.boot.availability.ReadinessState
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate

@ExtendWith(MockKExtension::class)
class HealthServiceTest {

    @MockK
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockK
    private lateinit var applicationAvailability: ApplicationAvailability

    private lateinit var healthService: HealthService

    @BeforeEach
    fun setUp() {
        healthService = HealthService(jdbcTemplate, applicationAvailability)
        every { applicationAvailability.readinessState } returns ReadinessState.ACCEPTING_TRAFFIC
    }

    private fun stubDatabase(result: Int) {
        every { jdbcTemplate.queryForObject("SELECT 1", any<Class<Int>>()) } returns result
    }

    @Test
    fun `should report UP when the application is ready and the database answers`() {
        stubDatabase(1)

        assertThat(healthService.checkHealth()).isEqualTo(HealthResponse(status = "UP", database = "UP"))
    }

    @Test
    fun `should report DOWN when the database cannot be reached`() {
        every { jdbcTemplate.queryForObject("SELECT 1", any<Class<Int>>()) } throws DataAccessResourceFailureException("Connection refused")

        assertThat(healthService.checkHealth()).isEqualTo(HealthResponse(status = "DOWN", database = "DOWN"))
    }

    @Test
    fun `should report DOWN when the database answers something unexpected`() {
        stubDatabase(0)

        assertThat(healthService.checkHealth()).isEqualTo(HealthResponse(status = "DOWN", database = "DOWN"))
    }

    @Test
    fun `should report STARTING without querying the database while the application is not ready`() {
        every { applicationAvailability.readinessState } returns ReadinessState.REFUSING_TRAFFIC

        assertThat(healthService.checkHealth()).isEqualTo(HealthResponse(status = "STARTING", database = "UNKNOWN"))
        verify(exactly = 0) { jdbcTemplate.queryForObject(any<String>(), any<Class<Int>>()) }
    }

    @Test
    fun `should query the database again on every check`() {
        stubDatabase(1)

        healthService.checkHealth()
        healthService.checkHealth()

        verify(exactly = 2) { jdbcTemplate.queryForObject("SELECT 1", any<Class<Int>>()) }
    }
}