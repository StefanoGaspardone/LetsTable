package com.backend.unit.utils

import com.backend.exceptions.EmailAlreadyTakenException
import com.backend.exceptions.UsernameAlreadyTakenException
import com.backend.utils.duplicateUserException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException

class UserConstraintTest {

    private val email = "marco@example.com"
    private val username = "marco"

    private fun violation(databaseMessage: String?) =
        DataIntegrityViolationException("could not execute statement", SQLException(databaseMessage, "23505"))

    @Test
    fun `should map a violation of the email constraint to EmailAlreadyTakenException`() {
        val e = violation("ERROR: duplicate key value violates unique constraint \"users_email_key\"\n  Detail: Key (email)=(marco@example.com) already exists.")

        assertThat(duplicateUserException(e, email, username)).isInstanceOf(EmailAlreadyTakenException::class.java)
    }

    @Test
    fun `should map a violation of a case-insensitive email index to EmailAlreadyTakenException`() {
        val e = violation("ERROR: duplicate key value violates unique constraint \"idx_users_email_lower\"\n  Detail: Key (lower(email::text))=(marco@example.com) already exists.")

        assertThat(duplicateUserException(e, email, username)).isInstanceOf(EmailAlreadyTakenException::class.java)
    }

    @Test
    fun `should map a violation of the username constraint to UsernameAlreadyTakenException`() {
        val e = violation("ERROR: duplicate key value violates unique constraint \"users_username_key\"\n  Detail: Key (username)=(marco) already exists.")

        assertThat(duplicateUserException(e, email, username)).isInstanceOf(UsernameAlreadyTakenException::class.java)
    }

    @Test
    fun `should not be fooled by a username that contains the word email`() {
        val e = violation("ERROR: duplicate key value violates unique constraint \"users_username_key\"\n  Detail: Key (username)=(email_master) already exists.")

        assertThat(duplicateUserException(e, "someone@example.com", "email_master")).isInstanceOf(UsernameAlreadyTakenException::class.java)
    }

    @Test
    fun `should ignore the case of the database message`() {
        val e = violation("ERROR: DUPLICATE KEY VALUE VIOLATES UNIQUE CONSTRAINT \"USERS_EMAIL_KEY\"")

        assertThat(duplicateUserException(e, email, username)).isInstanceOf(EmailAlreadyTakenException::class.java)
    }

    @Test
    fun `should fall back to UsernameAlreadyTakenException when the database gives no details`() {
        assertThat(duplicateUserException(violation(null), email, username)).isInstanceOf(UsernameAlreadyTakenException::class.java)
    }

    @Test
    fun `should use the exception itself when there is no underlying cause`() {
        val e = DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\"")

        assertThat(duplicateUserException(e, email, username)).isInstanceOf(EmailAlreadyTakenException::class.java)
    }
}