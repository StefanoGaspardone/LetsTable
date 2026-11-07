package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.CreateAdminRequest
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.models.specifications.UserSpecification
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UserRepository
import com.backend.services.AdminUserService
import com.backend.services.MatchService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.crypto.password.PasswordEncoder
import java.sql.SQLException
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminUserServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var matchRepository: MatchRepository

    @MockK
    private lateinit var collectionItemRepository: CollectionItemRepository

    @MockK(relaxed = true)
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockK
    private lateinit var matchService: MatchService

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @InjectMockKs
    private lateinit var adminUserService: AdminUserService

    private val adminId: UUID = UUID.randomUUID()
    private val userId: UUID = UUID.randomUUID()

    @AfterEach
    fun tearDown() {
        unmockkObject(UserSpecification)
    }

    private fun buildUser(
        id: UUID = UUID.randomUUID(),
        username: String = "user-${UUID.randomUUID()}",
        accountStatus: AccountStatus = AccountStatus.ACTIVE,
        role: UserRole = UserRole.USER,
    ) = User(
        id = id,
        username = username,
        email = "$username@example.com",
        passwordHash = "hash",
        role = role,
        accountStatus = accountStatus,
    )

    @Nested
    @DisplayName("listUsers")
    inner class ListUsersTests {

        private val pageableSlot = slot<Pageable>()

        private fun stubFindAll(users: List<User>) {
            every { userRepository.findAll(any<Specification<User>>(), capture(pageableSlot)) } answers {
                PageImpl(users, secondArg(), users.size.toLong())
            }
        }

        @Test
        fun `should return a page of users`() {
            stubFindAll(listOf(buildUser(id = userId)))

            val result = adminUserService.listUsers(null, null, null, 0, 20, null)

            assertThat(result.content.map { it.id }).containsExactly(userId)
        }

        @Test
        fun `should pass role, status and search to the specification`() {
            mockkObject(UserSpecification)
            stubFindAll(emptyList())

            adminUserService.listUsers(UserRole.ADMIN, AccountStatus.SUSPENDED, "marco", 0, 20, null)

            verify(exactly = 1) { UserSpecification.withFilters(UserRole.ADMIN, AccountStatus.SUSPENDED, "marco") }
        }

        @Test
        fun `should sort by newest first by default`() {
            stubFindAll(emptyList())

            adminUserService.listUsers(null, null, null, 0, 20, null)

            assertThat(pageableSlot.captured.sort.getOrderFor("createdAt")?.direction).isEqualTo(Sort.Direction.DESC)
        }

        @Test
        fun `should sort by the requested field`() {
            stubFindAll(emptyList())

            adminUserService.listUsers(null, null, null, 0, 20, "username-asc")

            assertThat(pageableSlot.captured.sort.getOrderFor("username")?.direction).isEqualTo(Sort.Direction.ASC)
        }

        @Test
        fun `should sanitize a negative page and an oversized size`() {
            stubFindAll(emptyList())

            adminUserService.listUsers(null, null, null, -5, 500, null)

            assertThat(pageableSlot.captured.pageNumber).isEqualTo(0)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(100)
        }

        @Test
        fun `should throw InvalidSortException without querying for an invalid sort field`() {
            assertThatThrownBy { adminUserService.listUsers(null, null, null, 0, 20, "email-asc") }
                .isInstanceOf(InvalidSortException::class.java)

            verify(exactly = 0) { userRepository.findAll(any<Specification<User>>(), any<Pageable>()) }
        }
    }

    @Nested
    @DisplayName("getUserDetail")
    inner class GetUserDetailTests {

        @Test
        fun `should return user detail with stats and the last ten matches`() {
            every { userRepository.findById(userId) } returns Optional.of(buildUser(id = userId))
            every { matchRepository.countCompletedMatchesForUser(userId) } returns 10L
            every { matchRepository.countWonMatchesForUser(userId) } returns 4L
            every { collectionItemRepository.countByUserId(userId) } returns 7L
            every { matchService.getRecentMatchesForUser(userId, 10) } returns emptyList()

            val result = adminUserService.getUserDetail(userId)

            assertThat(result.user.id).isEqualTo(userId)
            assertThat(result.totalMatches).isEqualTo(10L)
            assertThat(result.totalWins).isEqualTo(4L)
            assertThat(result.collectionCount).isEqualTo(7L)
            assertThat(result.recentMatches).isEmpty()
            verify(exactly = 1) { matchService.getRecentMatchesForUser(userId, 10) }
        }

        @Test
        fun `should throw UserNotFoundException without computing stats when user does not exist`() {
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { adminUserService.getUserDetail(userId) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { matchRepository.countCompletedMatchesForUser(any()) }
            verify(exactly = 0) { matchService.getRecentMatchesForUser(any(), any()) }
        }
    }

    @Nested
    @DisplayName("suspendUser")
    inner class SuspendUserTests {

        @Test
        fun `should suspend an active user and revoke their sessions`() {
            val user = buildUser(id = userId, accountStatus = AccountStatus.ACTIVE)
            every { userRepository.findById(userId) } returns Optional.of(user)

            val result = adminUserService.suspendUser(adminId, userId)

            assertThat(result.accountStatus).isEqualTo("SUSPENDED")
            assertThat(user.accountStatus).isEqualTo(AccountStatus.SUSPENDED)
            verify(exactly = 1) { refreshTokenRepository.revokeAllByUserId(userId) }
        }

        @Test
        fun `should throw CannotSuspendSelfException when admin tries to suspend themselves`() {
            assertThatThrownBy { adminUserService.suspendUser(adminId, adminId) }
                .isInstanceOf(CannotSuspendSelfException::class.java)

            verify(exactly = 0) { userRepository.findById(any()) }
            verify(exactly = 0) { refreshTokenRepository.revokeAllByUserId(any()) }
        }

        @Test
        fun `should throw UserNotFoundException when user does not exist`() {
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { adminUserService.suspendUser(adminId, userId) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { refreshTokenRepository.revokeAllByUserId(any()) }
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus::class, names = ["ACTIVE"], mode = EnumSource.Mode.EXCLUDE)
        fun `should refuse to suspend an account that is not active`(status: AccountStatus) {
            val user = buildUser(id = userId, accountStatus = status)
            every { userRepository.findById(userId) } returns Optional.of(user)

            assertThatThrownBy { adminUserService.suspendUser(adminId, userId) }
                .isInstanceOf(InvalidAccountStatusTransitionException::class.java)

            assertThat(user.accountStatus).isEqualTo(status)
            verify(exactly = 0) { refreshTokenRepository.revokeAllByUserId(any()) }
        }
    }

    @Nested
    @DisplayName("reactivateUser")
    inner class ReactivateUserTests {

        @Test
        fun `should reactivate a suspended user`() {
            val user = buildUser(id = userId, accountStatus = AccountStatus.SUSPENDED)
            every { userRepository.findById(userId) } returns Optional.of(user)

            val result = adminUserService.reactivateUser(userId)

            assertThat(result.accountStatus).isEqualTo("ACTIVE")
            assertThat(user.accountStatus).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        fun `should throw UserNotFoundException when user does not exist`() {
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { adminUserService.reactivateUser(userId) }
                .isInstanceOf(UserNotFoundException::class.java)
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus::class, names = ["SUSPENDED"], mode = EnumSource.Mode.EXCLUDE)
        fun `should refuse to reactivate an account that is not suspended`(status: AccountStatus) {
            val user = buildUser(id = userId, accountStatus = status)
            every { userRepository.findById(userId) } returns Optional.of(user)

            assertThatThrownBy { adminUserService.reactivateUser(userId) }
                .isInstanceOf(InvalidAccountStatusTransitionException::class.java)

            assertThat(user.accountStatus).isEqualTo(status)
        }
    }

    @Nested
    @DisplayName("createAdmin")
    inner class CreateAdminTests {

        private val savedUser = slot<User>()

        private fun stubAvailable(email: String, username: String) {
            every { userRepository.existsByEmailIgnoreCase(email) } returns false
            every { userRepository.existsByUsernameIgnoreCase(username) } returns false
            every { passwordEncoder.encode(any()) } answers { "hashed:${firstArg<String>()}" }
            every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured.apply { id = UUID.randomUUID() } }
        }

        @Test
        fun `should create an active admin with an encoded password`() {
            stubAvailable("newadmin@example.com", "newadmin")

            val result = adminUserService.createAdmin(CreateAdminRequest(username = "newadmin", email = "newadmin@example.com", password = "password123"))

            assertThat(result.role).isEqualTo("ADMIN")
            assertThat(result.accountStatus).isEqualTo("ACTIVE")
            assertThat(savedUser.captured.role).isEqualTo(UserRole.ADMIN)
            assertThat(savedUser.captured.accountStatus).isEqualTo(AccountStatus.ACTIVE)
            assertThat(savedUser.captured.passwordHash).isEqualTo("hashed:password123")
        }

        @Test
        fun `should normalize email, trim username and password`() {
            stubAvailable("newadmin@example.com", "newadmin")

            val result = adminUserService.createAdmin(CreateAdminRequest(username = "  NewAdmin  ", email = " NewAdmin@Example.com ", password = " password123 "))

            assertThat(result.username).isEqualTo("NewAdmin")
            assertThat(result.email).isEqualTo("newadmin@example.com")
            assertThat(savedUser.captured.passwordHash).isEqualTo("hashed:password123")
        }

        @Test
        fun `should throw EmailAlreadyTakenException without encoding or saving when email is taken`() {
            every { userRepository.existsByEmailIgnoreCase("taken@example.com") } returns true

            assertThatThrownBy { adminUserService.createAdmin(CreateAdminRequest(username = "newadmin", email = "taken@example.com", password = "password123")) }
                .isInstanceOf(EmailAlreadyTakenException::class.java)

            verify(exactly = 0) { passwordEncoder.encode(any()) }
            verify(exactly = 0) { userRepository.saveAndFlush(any()) }
        }

        @Test
        fun `should check the username case-insensitively`() {
            every { userRepository.existsByEmailIgnoreCase("newadmin@example.com") } returns false
            every { userRepository.existsByUsernameIgnoreCase("taken") } returns true

            assertThatThrownBy { adminUserService.createAdmin(CreateAdminRequest(username = "TAKEN", email = "newadmin@example.com", password = "password123")) }
                .isInstanceOf(UsernameAlreadyTakenException::class.java)

            verify(exactly = 0) { userRepository.saveAndFlush(any()) }
        }

        @Test
        fun `should map a concurrent email conflict to EmailAlreadyTakenException`() {
            stubAvailable("newadmin@example.com", "newadmin")
            every { userRepository.saveAndFlush(any()) } throws DataIntegrityViolationException(
                "could not execute statement",
                SQLException("duplicate key value violates unique constraint \"users_email_key\""),
            )

            assertThatThrownBy { adminUserService.createAdmin(CreateAdminRequest(username = "newadmin", email = "newadmin@example.com", password = "password123")) }
                .isInstanceOf(EmailAlreadyTakenException::class.java)
        }

        @Test
        fun `should map a concurrent username conflict to UsernameAlreadyTakenException`() {
            stubAvailable("newadmin@example.com", "newadmin")
            every { userRepository.saveAndFlush(any()) } throws DataIntegrityViolationException(
                "could not execute statement",
                SQLException("duplicate key value violates unique constraint \"users_username_key\""),
            )

            assertThatThrownBy { adminUserService.createAdmin(CreateAdminRequest(username = "newadmin", email = "newadmin@example.com", password = "password123")) }
                .isInstanceOf(UsernameAlreadyTakenException::class.java)
        }
    }
}