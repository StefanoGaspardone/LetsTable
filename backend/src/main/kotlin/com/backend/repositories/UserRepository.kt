package com.backend.repositories

import com.backend.models.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository: JpaRepository<User, UUID> {

    fun findByEmailIgnoreCase(email: String): Optional<User>

    fun findByUsernameIgnoreCase(username: String): Optional<User>

    fun existsByEmailIgnoreCase(email: String): Boolean

    fun existsByUsernameIgnoreCase(username: String): Boolean
}