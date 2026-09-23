package com.backend.schedulers

import com.backend.models.enums.AccountStatus
import com.backend.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class UserCleanupScheduler(
    private val userRepository: UserRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val INACTIVE_ACCOUNT_MAX_AGE_DAYS = 7L
    }

    @Scheduled(cron = "0 0 4 * * SUN")
    @Transactional
    fun deleteStaleInactiveAccounts() {
        logger.debug("\n\t[DEBUG] [user_cleanup_scheduler][delete_stale_inactive_accounts] Starting weekly cleanup of unverified accounts")

        try {
            val cutoff = Instant.now().minus(INACTIVE_ACCOUNT_MAX_AGE_DAYS, ChronoUnit.DAYS)
            val deletedCount = userRepository.deleteAllByAccountStatusAndCreatedAtBefore(AccountStatus.INACTIVE, cutoff)

            logger.info("\n\t[INFO] [user_cleanup_scheduler][delete_stale_inactive_accounts] Deleted {} unverified accounts older than {} days", deletedCount, INACTIVE_ACCOUNT_MAX_AGE_DAYS)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [user_cleanup_scheduler][delete_stale_inactive_accounts] Error deleting stale inactive accounts: {}", e.message, e)
        }
    }
}