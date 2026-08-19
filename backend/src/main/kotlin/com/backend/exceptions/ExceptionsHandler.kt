package com.backend.exceptions

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@Schema(description = "Standard error response")
data class ErrorResponse(
    @field:Schema(description = "Timestamp when the error occurred")
    val timestamp: Instant = Instant.now(),

    @field:Schema(description = "HTTP status code")
    val status: Int,

    @field:Schema(description = "HTTP reason phrase")
    val error: String,

    @field:Schema(description = "Human-readable error message")
    val message: String,
)

@RestControllerAdvice
class ExceptionsHandler {

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = HttpStatus.UNAUTHORIZED.reasonPhrase,
            message = ex.message ?: "Invalid credentials"
        )

        return ResponseEntity(errorResponse, HttpStatus.UNAUTHORIZED)
    }
}