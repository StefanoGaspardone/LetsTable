package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.CreateAdminRequest
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.UserRepository
import com.backend.services.AdminUserService
import com.backend.services.MatchService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.*

@ExtendWith(MockitoExtension::class)
class AdminUserServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var matchRepository: MatchRepository

    @Mock
    private lateinit var collectionItemRepository: CollectionItemRepository

    @Mock
    private lateinit var matchService: MatchService

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @InjectMocks
    private lateinit var adminUserService: AdminUserService

    private val adminId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

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

    // ---------------------------------------------------------------------
    // listUsers
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("listUsers")
    inner class ListUsersTests {

        @Test
        fun `should return a page of users`() {
            val user = buildUser(id = userId)

            whenever(userRepository.findAll(any<org.springframework.data.jpa.domain.Specification<User>>(), any<Pageable>()))
                .thenAnswer { invocation ->
                    val pageable = invocation.getArgument<Pageable>(1)
                    PageImpl(listOf(user), pageable, 1)
                }

            val result = adminUserService.listUsers(null, null, null, 0, 20, null)

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].id).isEqualTo(userId)
        }

        @Test
        fun `should sanitize a negative page number to zero`() {
            val pageableCaptor = mutableListOf<Pageable>()

            whenever(userRepository.findAll(any<org.springframework.data.jpa.domain.Specification<User>>(), any<Pageable>()))
                .thenAnswer { invocation ->
                    val pageable = invocation.getArgument<Pageable>(1)
                    pageableCaptor.add(pageable)
                    PageImpl<User>(emptyList(), pageable, 0)
                }

            adminUserService.listUsers(null, null, null, -5, 20, null)

            assertThat(pageableCaptor.first().pageNumber).isEqualTo(0)
        }

        @Test
        fun `should clamp size to a maximum of 100`() {
            val pageableCaptor = mutableListOf<Pageable>()

            whenever(userRepository.findAll(any<org.springframework.data.jpa.domain.Specification<User>>(), any<Pageable>()))
                .thenAnswer { invocation ->
                    val pageable = invocation.getArgument<Pageable>(1)
                    pageableCaptor.add(pageable)
                    PageImpl<User>(emptyList(), pageable, 0)
                }

            adminUserService.listUsers(null, null, null, 0, 500, null)

            assertThat(pageableCaptor.first().pageSize).isEqualTo(100)
        }

        @Test
        fun `should throw InvalidSortException for an invalid sort field`() {
            assertThatThrownBy {
                adminUserService.listUsers(null, null, null, 0, 20, "invalidField-asc")
            }.isInstanceOf(InvalidSortException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // getUserDetail
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("getUserDetail")
    inner class GetUserDetailTests {

        @Test
        fun `should return user detail with stats and recent matches`() {
            val user = buildUser(id = userId)

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(matchRepository.countCompletedMatchesForUser(userId)).thenReturn(10L)
            whenever(matchRepository.countWonMatchesForUser(userId)).thenReturn(4L)
            whenever(collectionItemRepository.countByUserId(userId)).thenReturn(7L)
            whenever(matchService.getRecentMatchesForUser(userId, 10)).thenReturn(emptyList())

            val result = adminUserService.getUserDetail(userId)

            assertThat(result.user.id).isEqualTo(userId)
            assertThat(result.totalMatches).isEqualTo(10L)
            assertThat(result.totalWins).isEqualTo(4L)
            assertThat(result.collectionCount).isEqualTo(7L)
        }

        @Test
        fun `should throw UserNotFoundException when user does not exist`() {
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                adminUserService.getUserDetail(userId)
            }.isInstanceOf(UserNotFoundException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // suspendUser
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("suspendUser")
    inner class SuspendUserTests {

        @Test
        fun `should suspend an active user`() {
            val user = buildUser(id = userId, accountStatus = AccountStatus.ACTIVE)

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] }

            val result = adminUserService.suspendUser(adminId, userId)

            assertThat(result.accountStatus).isEqualTo("SUSPENDED")
        }

        @Test
        fun `should throw CannotSuspendSelfException when admin tries to suspend themselves`() {
            assertThatThrownBy {
                adminUserService.suspendUser(adminId, adminId)
            }.isInstanceOf(CannotSuspendSelfException::class.java)

            verify(userRepository, never()).findById(any())
        }

        @Test
        fun `should throw UserNotFoundException when user does not exist`() {
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                adminUserService.suspendUser(adminId, userId)
            }.isInstanceOf(UserNotFoundException::class.java)
        }

        @Test
        fun `should throw InvalidAccountStatusTransitionException when user is not active`() {
            val user = buildUser(id = userId, accountStatus = AccountStatus.SUSPENDED)

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                adminUserService.suspendUser(adminId, userId)
            }.isInstanceOf(InvalidAccountStatusTransitionException::class.java)

            verify(userRepository, never()).save(any())
        }

        @Test
        fun `should throw InvalidAccountStatusTransitionException when user is inactive`() {
            val user = buildUser(id = userId, accountStatus = AccountStatus.INACTIVE)

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                adminUserService.suspendUser(adminId, userId)
            }.isInstanceOf(InvalidAccountStatusTransitionException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // reactivateUser
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("reactivateUser")
    inner class ReactivateUserTests {

        @Test
        fun `should reactivate a suspended user`() {
            val user = buildUser(id = userId, accountStatus = AccountStatus.SUSPENDED)

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] }

            val result = adminUserService.reactivateUser(userId)

            assertThat(result.accountStatus).isEqualTo("ACTIVE")
        }

        @Test
        fun `should throw UserNotFoundException when user does not exist`() {
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                adminUserService.reactivateUser(userId)
            }.isInstanceOf(UserNotFoundException::class.java)
        }

        @Test
        fun `should throw InvalidAccountStatusTransitionException when user is not suspended`() {
            val user = buildUser(id = userId, accountStatus = AccountStatus.ACTIVE)

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                adminUserService.reactivateUser(userId)
            }.isInstanceOf(InvalidAccountStatusTransitionException::class.java)

            verify(userRepository, never()).save(any())
        }
    }

    // ---------------------------------------------------------------------
    // createAdmin
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("createAdmin")
    inner class CreateAdminTests {

        @Test
        fun `should create a new admin with ACTIVE status`() {
            val request = CreateAdminRequest(username = "newadmin", email = "newadmin@example.com", password = "password123")

            whenever(userRepository.existsByEmailIgnoreCase("newadmin@example.com")).thenReturn(false)
            whenever(userRepository.existsByUsernameIgnoreCase("newadmin")).thenReturn(false)
            whenever(passwordEncoder.encode(anyString())).thenReturn("hashed")
            whenever(userRepository.saveAndFlush(any())).thenAnswer { invocation ->
                val user = invocation.arguments[0] as User
                user.id = UUID.randomUUID()
                user
            }

            val result = adminUserService.createAdmin(request)

            assertThat(result.role).isEqualTo("ADMIN")
            assertThat(result.accountStatus).isEqualTo("ACTIVE")
        }

        @Test
        fun `should throw EmailAlreadyTakenException when email is taken`() {
            val request = CreateAdminRequest(username = "newadmin", email = "taken@example.com", password = "password123")

            whenever(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true)

            assertThatThrownBy {
                adminUserService.createAdmin(request)
            }.isInstanceOf(EmailAlreadyTakenException::class.java)

            verify(userRepository, never()).saveAndFlush(any())
        }

        @Test
        fun `should throw UsernameAlreadyTakenException when username is taken`() {
            val request = CreateAdminRequest(username = "taken", email = "newadmin@example.com", password = "password123")

            whenever(userRepository.existsByEmailIgnoreCase("newadmin@example.com")).thenReturn(false)
            whenever(userRepository.existsByUsernameIgnoreCase("taken")).thenReturn(true)

            assertThatThrownBy {
                adminUserService.createAdmin(request)
            }.isInstanceOf(UsernameAlreadyTakenException::class.java)
        }

        @Test
        fun `should normalize email to lowercase and trim username`() {
            val request = CreateAdminRequest(username = "  NewAdmin  ", email = " NewAdmin@Example.com ", password = "password123")

            whenever(userRepository.existsByEmailIgnoreCase("newadmin@example.com")).thenReturn(false)
            whenever(userRepository.existsByUsernameIgnoreCase("newadmin")).thenReturn(false)
            whenever(passwordEncoder.encode(anyString())).thenReturn("hashed")
            whenever(userRepository.saveAndFlush(any())).thenAnswer { invocation ->
                val user = invocation.arguments[0] as User
                user.id = UUID.randomUUID()
                user
            }

            val result = adminUserService.createAdmin(request)

            assertThat(result.username).isEqualTo("NewAdmin")
            assertThat(result.email).isEqualTo("newadmin@example.com")
        }
    }
}