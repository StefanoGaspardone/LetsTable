package com.backend.seeders

import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdminSeeder(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value($$"${seeding.admin.username}") private val adminUsername: String,
    @Value($$"${seeding.admin.email}") private val adminEmail: String,
    @Value($$"${seeding.admin.password}") private val adminPassword: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun seed() {
        logger.debug("\n\t[DEBUG] [admin_seeder][seed] Seeding demo users")

        try {
            userRepository.findByUsernameIgnoreCase(adminUsername).orElseGet {
                val user = User(
                    username = adminUsername,
                    email = adminEmail,
                    passwordHash = passwordEncoder.encode(adminPassword)!!,
                    accountStatus = AccountStatus.ACTIVE,
                    notificationsEnabled = false,
                    role = UserRole.ADMIN
                )
                userRepository.save(user)
            }

            logger.info("\n\t[INFO] [admin_seeder][seed] admin user ready")
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_seeder][seed] Error seeding users: {}", e.message)
            throw e
        }
    }
}