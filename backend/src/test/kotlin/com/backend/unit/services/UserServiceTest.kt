package com.backend.unit.services

import com.backend.exceptions.UserNotFoundException
import com.backend.models.entities.*
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.*
import com.backend.services.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var matchRepository: MatchRepository

    @Mock
    private lateinit var matchPlayerRepository: MatchPlayerRepository

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @InjectMocks
    private lateinit var userService: UserService

    private val currentUserId: UUID = UUID.randomUUID()

    private fun buildUser(
        id: UUID = UUID.randomUUID(),
        username: String = "user-${UUID.randomUUID()}",
        email: String = "${UUID.randomUUID()}@example.com",
        accountStatus: AccountStatus = AccountStatus.ACTIVE,
    ) = User(
        id = id,
        username = username,
        email = email,
        passwordHash = "hash",
        role = UserRole.USER,
        accountStatus = accountStatus,
    )

    private fun buildGame() = Game(
        id = UUID.randomUUID(),
        bggId = 1L,
        name = "Test Game",
        lastSyncedAt = Instant.now(),
    )

    private fun buildMatch(id: UUID = UUID.randomUUID(), createdBy: User) = Match(
        id = id,
        game = buildGame(),
        createdBy = createdBy,
        playedAt = LocalDate.now(),
    )

    private fun buildMatchPlayer(match: Match, user: User? = null, guestName: String? = null) = MatchPlayer(
        id = UUID.randomUUID(),
        match = match,
        user = user,
        guestName = guestName,
    )

    private fun buildRefreshToken(user: User, revoked: Boolean = false) = RefreshToken(
        id = UUID.randomUUID(),
        user = user,
        tokenHash = "hash-${UUID.randomUUID()}",
        expiresAt = Instant.now().plusSeconds(3600),
        revoked = revoked,
    )

    // ---------------------------------------------------------------------
    // searchByUsername
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("searchByUsername")
    inner class SearchByUsernameTests {

        @Test
        fun `should return matching users excluding the current user`() {
            val otherUser = buildUser(username = "marco")
            val selfUser = buildUser(id = currentUserId, username = "marco-self")

            whenever(userRepository.searchActiveByUsername("marco"))
                .thenReturn(listOf(otherUser, selfUser))

            val result = userService.searchByUsername(currentUserId, "marco")

            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo(otherUser.id)
            verify(userRepository).searchActiveByUsername("marco")
        }

        @Test
        fun `should return empty list when no users match the query`() {
            whenever(userRepository.searchActiveByUsername("nonexistent"))
                .thenReturn(emptyList())

            val result = userService.searchByUsername(currentUserId, "nonexistent")

            assertThat(result).isEmpty()
        }

        @Test
        fun `should return empty list when only match is the current user itself`() {
            val selfUser = buildUser(id = currentUserId, username = "marco")

            whenever(userRepository.searchActiveByUsername("marco"))
                .thenReturn(listOf(selfUser))

            val result = userService.searchByUsername(currentUserId, "marco")

            assertThat(result).isEmpty()
        }

        @Test
        fun `should rethrow exception when repository fails`() {
            whenever(userRepository.searchActiveByUsername(any()))
                .thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                userService.searchByUsername(currentUserId, "marco")
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // getUserById
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("getUserById")
    inner class GetUserByIdTests {

        @Test
        fun `should return user DTO when user exists`() {
            val user = buildUser(id = currentUserId, username = "stefano")
            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))

            val result = userService.getUserById(currentUserId)

            assertThat(result.id).isEqualTo(currentUserId)
            assertThat(result.username).isEqualTo("stefano")
        }

        @Test
        fun `should throw UserNotFoundException when user does not exist`() {
            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                userService.getUserById(currentUserId)
            }.isInstanceOf(UserNotFoundException::class.java)
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            whenever(userRepository.findById(currentUserId))
                .thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                userService.getUserById(currentUserId)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // deleteAccount
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("deleteAccount")
    inner class DeleteAccountTests {

        @Test
        fun `should throw UserNotFoundException when user does not exist`() {
            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                userService.deleteAccount(currentUserId)
            }.isInstanceOf(UserNotFoundException::class.java)

            verify(matchRepository, never()).findAllForUser(any())
            verify(userRepository, never()).save(any())
        }

        @Test
        fun `should delete solo match where the user is the only player`() {
            val user = buildUser(id = currentUserId)
            val match = buildMatch(createdBy = user)
            val soloPlayer = buildMatchPlayer(match = match, user = user)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(listOf(match))
            whenever(matchPlayerRepository.findAllByMatchId(match.id!!)).thenReturn(listOf(soloPlayer))
            whenever(refreshTokenRepository.findAll()).thenReturn(emptyList())

            userService.deleteAccount(currentUserId)

            verify(matchRepository).delete(match)
        }

        @Test
        fun `should not delete match when it has more than one player`() {
            val user = buildUser(id = currentUserId)
            val otherUser = buildUser()
            val match = buildMatch(createdBy = user)
            val player1 = buildMatchPlayer(match = match, user = user)
            val player2 = buildMatchPlayer(match = match, user = otherUser)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(listOf(match))
            whenever(matchPlayerRepository.findAllByMatchId(match.id!!)).thenReturn(listOf(player1, player2))
            whenever(refreshTokenRepository.findAll()).thenReturn(emptyList())

            userService.deleteAccount(currentUserId)

            verify(matchRepository, never()).delete(any<Match>())
        }

        @Test
        fun `should not delete match when the only player is a guest, not the user`() {
            val user = buildUser(id = currentUserId)
            val match = buildMatch(createdBy = user)
            val guestPlayer = buildMatchPlayer(match = match, user = null, guestName = "Guest Player")

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(listOf(match))
            whenever(matchPlayerRepository.findAllByMatchId(match.id!!)).thenReturn(listOf(guestPlayer))
            whenever(refreshTokenRepository.findAll()).thenReturn(emptyList())

            userService.deleteAccount(currentUserId)

            verify(matchRepository, never()).delete(any<Match>())
        }

        @Test
        fun `should not delete match when the only player is a different user`() {
            val user = buildUser(id = currentUserId)
            val otherUser = buildUser()
            val match = buildMatch(createdBy = user)
            val otherPlayer = buildMatchPlayer(match = match, user = otherUser)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(listOf(match))
            whenever(matchPlayerRepository.findAllByMatchId(match.id!!)).thenReturn(listOf(otherPlayer))
            whenever(refreshTokenRepository.findAll()).thenReturn(emptyList())

            userService.deleteAccount(currentUserId)

            verify(matchRepository, never()).delete(any<Match>())
        }

        @Test
        fun `should not delete match with zero players`() {
            val user = buildUser(id = currentUserId)
            val match = buildMatch(createdBy = user)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(listOf(match))
            whenever(matchPlayerRepository.findAllByMatchId(match.id!!)).thenReturn(emptyList())
            whenever(refreshTokenRepository.findAll()).thenReturn(emptyList())

            userService.deleteAccount(currentUserId)

            verify(matchRepository, never()).delete(any<Match>())
        }

        @Test
        fun `should process multiple matches deleting only the eligible solo ones`() {
            val user = buildUser(id = currentUserId)
            val otherUser = buildUser()

            val soloMatch = buildMatch(createdBy = user)
            val soloPlayer = buildMatchPlayer(match = soloMatch, user = user)

            val sharedMatch = buildMatch(createdBy = user)
            val sharedPlayer1 = buildMatchPlayer(match = sharedMatch, user = user)
            val sharedPlayer2 = buildMatchPlayer(match = sharedMatch, user = otherUser)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(listOf(soloMatch, sharedMatch))
            whenever(matchPlayerRepository.findAllByMatchId(soloMatch.id!!)).thenReturn(listOf(soloPlayer))
            whenever(matchPlayerRepository.findAllByMatchId(sharedMatch.id!!)).thenReturn(listOf(sharedPlayer1, sharedPlayer2))
            whenever(refreshTokenRepository.findAll()).thenReturn(emptyList())

            userService.deleteAccount(currentUserId)

            verify(matchRepository, times(1)).delete(soloMatch)
            verify(matchRepository, never()).delete(sharedMatch)
        }

        @Test
        fun `should revoke active refresh tokens belonging to the user`() {
            val user = buildUser(id = currentUserId)
            val activeToken = buildRefreshToken(user = user, revoked = false)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(emptyList())
            whenever(refreshTokenRepository.findAll()).thenReturn(listOf(activeToken))

            userService.deleteAccount(currentUserId)

            assertThat(activeToken.revoked).isTrue()
            verify(refreshTokenRepository).save(activeToken)
        }

        @Test
        fun `should not re-save an already revoked token belonging to the user`() {
            val user = buildUser(id = currentUserId)
            val alreadyRevokedToken = buildRefreshToken(user = user, revoked = true)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(emptyList())
            whenever(refreshTokenRepository.findAll()).thenReturn(listOf(alreadyRevokedToken))

            userService.deleteAccount(currentUserId)

            verify(refreshTokenRepository, never()).save(any())
        }

        @Test
        fun `should not touch refresh tokens belonging to a different user`() {
            val user = buildUser(id = currentUserId)
            val otherUser = buildUser()
            val otherUserToken = buildRefreshToken(user = otherUser, revoked = false)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(emptyList())
            whenever(refreshTokenRepository.findAll()).thenReturn(listOf(otherUserToken))

            userService.deleteAccount(currentUserId)

            assertThat(otherUserToken.revoked).isFalse()
            verify(refreshTokenRepository, never()).save(any())
        }

        @Test
        fun `should revoke only the eligible tokens among a mixed list`() {
            val user = buildUser(id = currentUserId)
            val otherUser = buildUser()

            val activeOwnToken = buildRefreshToken(user = user, revoked = false)
            val revokedOwnToken = buildRefreshToken(user = user, revoked = true)
            val otherUserToken = buildRefreshToken(user = otherUser, revoked = false)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(emptyList())
            whenever(refreshTokenRepository.findAll())
                .thenReturn(listOf(activeOwnToken, revokedOwnToken, otherUserToken))

            userService.deleteAccount(currentUserId)

            verify(refreshTokenRepository, times(1)).save(activeOwnToken)
            verify(refreshTokenRepository, never()).save(revokedOwnToken)
            verify(refreshTokenRepository, never()).save(otherUserToken)
        }

        @Test
        fun `should anonymize username, email, password hash and set account status to DELETED`() {
            val userId = currentUserId
            val user = buildUser(id = userId, username = "stefano", email = "stefano@example.com", accountStatus = AccountStatus.ACTIVE)

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(userId)).thenReturn(emptyList())
            whenever(refreshTokenRepository.findAll()).thenReturn(emptyList())

            userService.deleteAccount(userId)

            assertThat(user.username).isEqualTo("deleted-user-$userId")
            assertThat(user.email).isEqualTo("deleted-$userId@letstable.invalid")
            assertThat(user.passwordHash).isEmpty()
            assertThat(user.accountStatus).isEqualTo(AccountStatus.DELETED)
            verify(userRepository).save(user)
        }

        @Test
        fun `should return success message when account is deleted`() {
            val user = buildUser(id = currentUserId)

            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId)).thenReturn(emptyList())
            whenever(refreshTokenRepository.findAll()).thenReturn(emptyList())

            val result = userService.deleteAccount(currentUserId)

            assertThat(result.message).isEqualTo("Your account has been deleted")
        }

        @Test
        fun `should rethrow generic exception when a repository call fails unexpectedly`() {
            val user = buildUser(id = currentUserId)
            whenever(userRepository.findById(currentUserId)).thenReturn(Optional.of(user))
            whenever(matchRepository.findAllForUser(currentUserId))
                .thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                userService.deleteAccount(currentUserId)
            }.isInstanceOf(RuntimeException::class.java)

            verify(userRepository, never()).save(any())
        }
    }
}