package com.backend.utils

import com.backend.exceptions.EmailAlreadyTakenException
import com.backend.exceptions.UsernameAlreadyTakenException
import org.springframework.dao.DataIntegrityViolationException

fun duplicateUserException(e: DataIntegrityViolationException, email: String, username: String): RuntimeException {
    val detail = e.mostSpecificCause.message.orEmpty().lowercase()
    return if(detail.contains("email")) EmailAlreadyTakenException(email) else UsernameAlreadyTakenException(username)
}