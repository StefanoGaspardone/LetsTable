package com.backend.seeders

import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

data class SeedUserSpec(val username: String, val email: String)

@Component
class UserSeeder(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val specs = listOf(
        SeedUserSpec("marco", "marco@letstable.com"),
        SeedUserSpec("anna", "anna@letstable.com"),
        SeedUserSpec("luca", "luca@letstable.com"),
        SeedUserSpec("elena", "elena@letstable.com"),
    )

    fun seed(): List<User> {
        logger.debug("\n\t[DEBUG] [user_seeder][seed] Seeding demo users")

        try {
            val users = specs.map { spec ->
                userRepository.findByUsernameIgnoreCase(spec.username).orElseGet {
                    val user = User(
                        username = spec.username,
                        email = spec.email,
                        passwordHash = passwordEncoder.encode("password")!!,
                        accountStatus = AccountStatus.ACTIVE,
                    )
                    userRepository.save(user)
                }
            }

            logger.info("\n\t[INFO] [user_seeder][seed] {} demo users ready", users.size)
            return users
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [user_seeder][seed] Error seeding users: {}", e.message)
            throw e
        }
    }
}