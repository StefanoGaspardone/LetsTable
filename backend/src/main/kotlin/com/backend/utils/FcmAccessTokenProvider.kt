package com.backend.utils

import com.google.auth.oauth2.GoogleCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.FileInputStream

fun interface FcmAccessTokenProvider {
    fun accessToken(): String
}

@Component
class GoogleFcmAccessTokenProvider internal constructor(
    private val credentialsFactory: () -> GoogleCredentials,
): FcmAccessTokenProvider {

    @Autowired
    constructor(@Value($$"${fcm.credentials-path:}") credentialsPath: String): this({ loadCredentials(credentialsPath) })

    companion object {
        private const val MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

        private fun loadCredentials(credentialsPath: String): GoogleCredentials =
            if(credentialsPath.isBlank()) {
                GoogleCredentials.getApplicationDefault()
            } else {
                FileInputStream(credentialsPath).use { GoogleCredentials.fromStream(it) }
            }
    }

    private val credentials: GoogleCredentials by lazy {
        credentialsFactory().createScoped(listOf(MESSAGING_SCOPE))
    }

    @Synchronized
    override fun accessToken(): String {
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }
}