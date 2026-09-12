package com.backend.integration.controllers

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class HealthControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Nested
    @DisplayName("GET /api/v1/health")
    inner class CheckHealthTests {

        @Test
        fun `should return 200 OK with body 'OK'`() {
            mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk)
                .andExpect(content().string("OK"))
        }
    }
}