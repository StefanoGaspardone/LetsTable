package com.backend.unit.utils

import com.backend.utils.GoogleFcmAccessTokenProvider
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GoogleFcmAccessTokenProviderTest {

    private fun tokenExpiringIn(value: String, duration: Duration) =
        AccessToken(value, Date.from(Instant.now().plus(duration)))

    private class RefreshingCredentials(initial: AccessToken, private val next: () -> AccessToken): GoogleCredentials(initial) {
        val refreshes = AtomicInteger()

        override fun refreshAccessToken(): AccessToken {
            refreshes.incrementAndGet()
            return next()
        }
    }

    @Test
    fun `should return the current access token when it is still valid`() {
        val credentials = RefreshingCredentials(tokenExpiringIn("valid-token", Duration.ofHours(1))) { error("should not refresh") }
        val provider = GoogleFcmAccessTokenProvider { credentials }

        assertEquals("valid-token", provider.accessToken())
        assertEquals(0, credentials.refreshes.get())
    }

    @Test
    fun `should refresh the access token when it has expired`() {
        val credentials = RefreshingCredentials(tokenExpiringIn("old-token", Duration.ofMinutes(-1))) {
            tokenExpiringIn("new-token", Duration.ofHours(1))
        }
        val provider = GoogleFcmAccessTokenProvider { credentials }

        assertEquals("new-token", provider.accessToken())
        assertEquals("new-token", provider.accessToken())
        assertEquals(1, credentials.refreshes.get())
    }

    @Test
    fun `should not load the credentials until a token is needed`() {
        val loads = AtomicInteger()

        GoogleFcmAccessTokenProvider { loads.incrementAndGet(); error("should not load") }

        assertEquals(0, loads.get())
    }

    @Test
    fun `should load the credentials only once`() {
        val loads = AtomicInteger()
        val provider = GoogleFcmAccessTokenProvider {
            loads.incrementAndGet()
            GoogleCredentials.create(tokenExpiringIn("valid-token", Duration.ofHours(1)))
        }

        repeat(3) { provider.accessToken() }

        assertEquals(1, loads.get())
    }

    @Test
    fun `should load the credentials only once under concurrent calls`() {
        val loads = AtomicInteger()
        val provider = GoogleFcmAccessTokenProvider {
            loads.incrementAndGet()
            Thread.sleep(50)
            GoogleCredentials.create(tokenExpiringIn("valid-token", Duration.ofHours(1)))
        }
        val executor = Executors.newFixedThreadPool(8)

        val tokens = (1..16).map { executor.submit<String> { provider.accessToken() } }.map { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, loads.get())
        assertTrue(tokens.all { it == "valid-token" })
    }

    @Test
    fun `should propagate a failure while loading the credentials`() {
        val provider = GoogleFcmAccessTokenProvider { throw IllegalStateException("No credentials") }

        val error = assertThrows<IllegalStateException> { provider.accessToken() }

        assertEquals("No credentials", error.message)
    }

    @Test
    fun `should fail on the first token request when the credentials file does not exist`() {
        val provider = GoogleFcmAccessTokenProvider("/does/not/exist/letstable-firebase.json")

        assertThrows<FileNotFoundException> { provider.accessToken() }
    }
}