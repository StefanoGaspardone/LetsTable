package com.backend.utils

import com.backend.exceptions.EmailAlreadyTakenException
import com.backend.exceptions.UsernameAlreadyTakenException
import org.springframework.dao.DataIntegrityViolationException

private val EMAIL_VIOLATION_REGEX = Regex("""constraint "[^"]*email[^"]*"|key \((lower\()?email""")

fun duplicateUserException(e: DataIntegrityViolationException, email: String, username: String): RuntimeException {
    val detail = e.mostSpecificCause.message.orEmpty().lowercase()
    return if(EMAIL_VIOLATION_REGEX.containsMatchIn(detail)) EmailAlreadyTakenException(email) else UsernameAlreadyTakenException(username)
}