package com.backend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Disabilitato fino alla configurazione dei test d'integrazione E2E con Testcontainers")
class BackendApplicationTests {

    @Test
    fun contextLoads() {
    }
}
