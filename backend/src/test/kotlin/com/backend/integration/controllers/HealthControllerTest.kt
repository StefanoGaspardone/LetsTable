package com.backend.integration.controllers

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.willReturn
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.availability.ApplicationAvailability
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class HealthControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoSpyBean
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoSpyBean
    private lateinit var applicationAvailability: ApplicationAvailability

    companion object {
        private const val URL = "/api/v1/health"
    }

    private fun databaseDown() =
        willThrow(CannotGetJdbcConnectionException("Connection refused"))
            .given(jdbcTemplate).queryForObject(eq("SELECT 1"), any<Class<Int>>())

    @Nested
    @DisplayName("GET /api/v1/health")
    inner class CheckHealthTests {

        @Test
        fun `should return 200 with application and database up`() {
            mockMvc.perform(get(URL))
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
        }

        @Test
        fun `should really query the database`() {
            mockMvc.perform(get(URL)).andExpect(status().isOk)

            verify(jdbcTemplate).queryForObject(eq("SELECT 1"), any<Class<Int>>())
        }

        @Test
        fun `should return 503 when the database is unreachable`() {
            databaseDown()

            mockMvc.perform(get(URL))
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database").value("DOWN"))
        }

        @Test
        fun `should go back up once the database is reachable again`() {
            databaseDown()
            mockMvc.perform(get(URL)).andExpect(status().isServiceUnavailable)

            reset(jdbcTemplate)

            mockMvc.perform(get(URL))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("UP"))
        }

        @Test
        fun `should return 503 without touching the database while the application is starting`() {
            willReturn(ReadinessState.REFUSING_TRAFFIC).given(applicationAvailability).readinessState

            mockMvc.perform(get(URL))
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.status").value("STARTING"))
                .andExpect(jsonPath("$.database").value("UNKNOWN"))

            verify(jdbcTemplate, never()).queryForObject(eq("SELECT 1"), any<Class<Int>>())
        }

        @Test
        fun `should be reachable without authentication`() {
            mockMvc.perform(get(URL)).andExpect(status().isOk)
        }

        @Test
        fun `should answer even with an invalid token`() {
            mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isOk)
        }

        @Test
        fun `should not accept other methods`() {
            mockMvc.perform(post(URL)).andExpect(status().is4xxClientError)
        }
    }
}