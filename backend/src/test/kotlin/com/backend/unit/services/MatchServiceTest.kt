package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.*
import com.backend.models.entities.*
import com.backend.models.enums.AccountStatus
import com.backend.models.projections.GameMatchStatsProjection
import com.backend.models.projections.MatchDayCountProjection
import com.backend.repositories.*
import com.backend.services.MatchService
import com.backend.services.UserAchievementService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class MatchServiceTest {

    private lateinit var matchRepository: MatchRepository
    private lateinit var matchTeamRepository: MatchTeamRepository
    private lateinit var matchPlayerRepository: MatchPlayerRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var userRepository: UserRepository
    private lateinit var userAchievementService: UserAchievementService
    private lateinit var matchService: MatchService

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val gameId = UUID.randomUUID()
    private val matchId = UUID.randomUUID()
    private val expansionId = UUID.randomUUID()

    private lateinit var user: User
    private lateinit var otherUser: User
    private lateinit var game: Game
    private lateinit var expansion: Game

    private val knownUsers = mutableMapOf<UUID, User>()
    private val savedMatches = mutableListOf<Match>()
    private val savedTeams = mutableListOf<MatchTeam>()
    private val savedPlayers = mutableListOf<MatchPlayer>()

    @BeforeEach
    fun setUp() {
        matchRepository = mockk(relaxUnitFun = true)
        matchTeamRepository = mockk(relaxUnitFun = true)
        matchPlayerRepository = mockk(relaxUnitFun = true)
        gameRepository = mockk()
        userRepository = mockk()
        userAchievementService = mockk(relaxUnitFun = true)

        matchService = MatchService(matchRepository, matchTeamRepository, matchPlayerRepository, gameRepository, userRepository, userAchievementService)

        user = User(id = userId, username = "stefano", email = "stefano@example.com", passwordHash = "hash")
        otherUser = User(id = otherUserId, username = "marco", email = "marco@example.com", passwordHash = "hash")
        expansion = Game(id = expansionId, bggId = 500L, name = "Catan: Seafarers")
        game = Game(id = gameId, bggId = 13L, name = "Catan", expansionRefs = listOf(ExpansionRef(500L, "Catan: Seafarers")))

        knownUsers.clear()
        knownUsers[userId] = user
        knownUsers[otherUserId] = otherUser
        savedMatches.clear()
        savedTeams.clear()
        savedPlayers.clear()

        every { gameRepository.findById(gameId) } returns Optional.of(game)
        every { gameRepository.findAllById(any<Iterable<UUID>>()) } answers { firstArg<Iterable<UUID>>().mapNotNull { if(it == expansionId) expansion else null } }
        every { userRepository.findById(any<UUID>()) } answers { Optional.ofNullable(knownUsers[firstArg<UUID>()]) }
        every { userRepository.findAllById(any<Iterable<UUID>>()) } answers { firstArg<Iterable<UUID>>().mapNotNull { knownUsers[it] } }
        every { matchRepository.save(any<Match>()) } answers { firstArg<Match>().apply { id = matchId }.also { savedMatches += it } }
        every { matchTeamRepository.saveAll(any<Iterable<MatchTeam>>()) } answers { firstArg<Iterable<MatchTeam>>().toList().onEach { it.id = UUID.randomUUID() }.also { savedTeams += it } }
        every { matchPlayerRepository.saveAll(any<Iterable<MatchPlayer>>()) } answers { firstArg<Iterable<MatchPlayer>>().toList().onEach { it.id = UUID.randomUUID() }.also { savedPlayers += it } }
    }

    private fun player(userId: UUID? = null, guestName: String? = null, score: Int = 0, isWinner: Boolean = false) =
        MatchIndividualPlayerRequest(userId, guestName, "Red", score, isWinner = isWinner, isStartingFirst = false)

    private fun identity(userId: UUID? = null, guestName: String? = null) =
        MatchPlayerIdentityRequest(userId = userId, guestName = guestName)

    private fun team(name: String, score: Int, isWinner: Boolean, players: List<MatchPlayerIdentityRequest>) =
        CreateMatchTeamRequest(name = name, color = "Blue", score = score, isWinner = isWinner, isStartingFirst = false, players = players)

    private fun createRequest(
        players: List<MatchIndividualPlayerRequest>? = listOf(player(userId = userId)),
        teams: List<CreateMatchTeamRequest>? = null,
        isTeamBased: Boolean = false,
        durationMinutes: Int? = 60,
        expansionIds: List<UUID> = emptyList(),
        playedAt: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ) = CreateMatchRequest(
        gameId = gameId,
        isTeamBased = isTeamBased,
        playedAt = playedAt,
        place = "Casa",
        notes = "Partita tirata",
        durationMinutes = durationMinutes,
        expansionIds = expansionIds,
        teams = teams,
        players = players,
    )

    private fun updateRequest(
        players: List<MatchIndividualPlayerRequest> = listOf(player(userId = userId, score = 10, isWinner = true)),
        durationMinutes: Int? = null,
        expansionIds: List<UUID> = emptyList(),
        playedAt: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ) = UpdateMatchRequest(
        gameId = gameId,
        isTeamBased = false,
        playedAt = playedAt,
        place = "Club",
        notes = "Rivincita",
        durationMinutes = durationMinutes,
        expansionIds = expansionIds,
        teams = null,
        players = players,
    )

    private fun existingMatch(
        durationMinutes: Int? = 45,
        createdBy: User = user,
        playedAt: Instant = Instant.now(),
        createdAt: Instant = Instant.now(),
        isTeamBased: Boolean = false,
    ) = Match(
        id = matchId,
        game = game,
        createdBy = createdBy,
        isTeamBased = isTeamBased,
        playedAt = playedAt,
        createdAt = createdAt,
        durationMinutes = durationMinutes,
    ).also { every { matchRepository.findById(matchId) } returns Optional.of(it) }

    private fun verifyNoAchievementEvaluation() {
        verify(exactly = 0) { userAchievementService.evaluateAfterCommit(any()) }
    }

    private fun verifyNothingPersisted() {
        verify(exactly = 0) { matchRepository.save(any<Match>()) }
        verify(exactly = 0) { matchPlayerRepository.saveAll(any<Iterable<MatchPlayer>>()) }
        verify(exactly = 0) { matchTeamRepository.saveAll(any<Iterable<MatchTeam>>()) }
    }

    @Nested
    @DisplayName("createMatch")
    inner class CreateMatchTests {

        @Test
        fun `should save the match with the request data`() {
            val request = createRequest()

            val result = matchService.createMatch(userId, request)

            val match = savedMatches.single()
            assertThat(result.id).isEqualTo(matchId)
            assertThat(match.game).isSameAs(game)
            assertThat(match.createdBy).isSameAs(user)
            assertThat(match.isTeamBased).isFalse()
            assertThat(match.place).isEqualTo("Casa")
            assertThat(match.notes).isEqualTo("Partita tirata")
            assertThat(match.durationMinutes).isEqualTo(60)
            assertThat(match.playedAt.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(request.playedAt)
        }

        @Test
        fun `should keep the requested date even when it is in the past`() {
            val playedAt = LocalDate.of(2026, 3, 15)

            matchService.createMatch(userId, createRequest(playedAt = playedAt))

            assertThat(savedMatches.single().playedAt.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(playedAt)
        }

        @Test
        fun `should save individual players linking registered users and keeping guests by name`() {
            val request = createRequest(players = listOf(player(userId = userId, score = 10, isWinner = true), player(guestName = "Luca", score = 4)))

            val result = matchService.createMatch(userId, request)

            assertThat(savedPlayers).hasSize(2)
            assertThat(savedPlayers[0].user).isSameAs(user)
            assertThat(savedPlayers[0].guestName).isNull()
            assertThat(savedPlayers[0].score).isEqualTo(10)
            assertThat(savedPlayers[0].team).isNull()
            assertThat(savedPlayers[1].user).isNull()
            assertThat(savedPlayers[1].guestName).isEqualTo("Luca")
            assertThat(savedPlayers).allMatch { it.match.id == matchId }
            assertThat(result.players).hasSize(2)
            assertThat(result.teams).isNull()
            verify(exactly = 0) { matchTeamRepository.saveAll(any<Iterable<MatchTeam>>()) }
        }

        @Test
        fun `should save teams and link each player to its team`() {
            val request = createRequest(
                isTeamBased = true,
                players = null,
                teams = listOf(
                    team("A", 20, true, listOf(identity(userId = userId))),
                    team("B", 10, false, listOf(identity(userId = otherUserId), identity(guestName = "Luca"))),
                ),
            )

            val result = matchService.createMatch(userId, request)

            assertThat(savedTeams.map { it.name }).containsExactly("A", "B")
            assertThat(savedPlayers).hasSize(3)
            assertThat(savedPlayers[0].team).isSameAs(savedTeams[0])
            assertThat(savedPlayers.drop(1)).allMatch { it.team === savedTeams[1] }
            assertThat(savedPlayers[2].guestName).isEqualTo("Luca")
            assertThat(result.isTeamBased).isTrue()
            assertThat(result.players).isNull()
            assertThat(result.teams!!.map { it.players.size }).containsExactly(1, 2)
        }

        @Test
        fun `should set the winner score from the best winning player`() {
            val request = createRequest(players = listOf(
                player(userId = userId, score = 12, isWinner = true),
                player(userId = otherUserId, score = 15, isWinner = true),
                player(guestName = "Luca", score = 30),
            ))

            matchService.createMatch(userId, request)

            assertThat(savedMatches.single().winnerScore).isEqualTo(15)
        }

        @Test
        fun `should set the winner score from the winning team`() {
            val request = createRequest(
                isTeamBased = true,
                players = null,
                teams = listOf(team("A", 20, true, listOf(identity(userId = userId))), team("B", 35, false, listOf(identity(guestName = "Luca")))),
            )

            matchService.createMatch(userId, request)

            assertThat(savedMatches.single().winnerScore).isEqualTo(20)
        }

        @Test
        fun `should leave the winner score empty when nobody won`() {
            matchService.createMatch(userId, createRequest(players = listOf(player(userId = userId, score = 8))))

            assertThat(savedMatches.single().winnerScore).isNull()
        }

        @Test
        fun `should evaluate achievements only for registered participants`() {
            val request = createRequest(players = listOf(player(userId = userId), player(userId = otherUserId), player(guestName = "Luca")))

            matchService.createMatch(userId, request)

            verify(exactly = 1) { userAchievementService.evaluateAfterCommit(setOf(userId, otherUserId)) }
        }

        @Test
        fun `should evaluate achievements for registered players of every team`() {
            val request = createRequest(
                isTeamBased = true,
                players = null,
                teams = listOf(team("A", 20, true, listOf(identity(userId = userId))), team("B", 10, false, listOf(identity(userId = otherUserId), identity(guestName = "Luca")))),
            )

            matchService.createMatch(userId, request)

            verify(exactly = 1) { userAchievementService.evaluateAfterCommit(setOf(userId, otherUserId)) }
        }

        @Test
        fun `should not evaluate achievements nor set the winner score for a match in progress`() {
            matchService.createMatch(userId, createRequest(durationMinutes = null, players = listOf(player(userId = userId, score = 10, isWinner = true))))

            assertThat(savedMatches.single().winnerScore).isNull()
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should allow a match with only guests`() {
            matchService.createMatch(userId, createRequest(players = listOf(player(guestName = "Luca"), player(guestName = "Anna"))))

            assertThat(savedPlayers).allMatch { it.user == null }
            verify(exactly = 0) { userRepository.findAllById(any<Iterable<UUID>>()) }
            verify(exactly = 1) { userAchievementService.evaluateAfterCommit(emptySet()) }
        }

        @Test
        fun `should attach the expansions used`() {
            val result = matchService.createMatch(userId, createRequest(expansionIds = listOf(expansionId)))

            assertThat(savedMatches.single().expansionsUsed).containsExactly(expansion)
            assertThat(result.expansionsUsed.map { it.bggId }).containsExactly(500L)
        }

        @Test
        fun `should not look up expansions when none are requested`() {
            val result = matchService.createMatch(userId, createRequest())

            assertThat(result.expansionsUsed).isEmpty()
            verify(exactly = 0) { gameRepository.findAllById(any<Iterable<UUID>>()) }
        }

        @Test
        fun `should throw GameNotFoundException when an expansion does not exist`() {
            val missingId = UUID.randomUUID()

            assertThatThrownBy { matchService.createMatch(userId, createRequest(expansionIds = listOf(expansionId, missingId))) }
                .isInstanceOf(GameNotFoundException::class.java)

            verifyNothingPersisted()
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should throw InvalidExpansionForGameException when the expansion belongs to another game`() {
            game = Game(id = gameId, bggId = 13L, name = "Catan", expansionRefs = emptyList())
            every { gameRepository.findById(gameId) } returns Optional.of(game)

            assertThatThrownBy { matchService.createMatch(userId, createRequest(expansionIds = listOf(expansionId))) }
                .isInstanceOf(InvalidExpansionForGameException::class.java)

            verifyNothingPersisted()
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should throw GameNotFoundException when the game does not exist`() {
            every { gameRepository.findById(gameId) } returns Optional.empty()

            assertThatThrownBy { matchService.createMatch(userId, createRequest()) }
                .isInstanceOf(GameNotFoundException::class.java)

            verifyNothingPersisted()
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when the creator does not exist`() {
            knownUsers.remove(userId)

            assertThatThrownBy { matchService.createMatch(userId, createRequest(players = listOf(player(guestName = "Luca")))) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verifyNothingPersisted()
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when the creator deleted the account`() {
            user.accountStatus = AccountStatus.DELETED

            assertThatThrownBy { matchService.createMatch(userId, createRequest(players = listOf(player(guestName = "Luca")))) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verifyNothingPersisted()
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when a participant does not exist`() {
            val ghostId = UUID.randomUUID()

            assertThatThrownBy { matchService.createMatch(userId, createRequest(players = listOf(player(userId = userId), player(userId = ghostId)))) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)
                .hasMessageContaining(ghostId.toString())

            verify(exactly = 0) { matchPlayerRepository.saveAll(any<Iterable<MatchPlayer>>()) }
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when a participant deleted the account`() {
            otherUser.accountStatus = AccountStatus.DELETED

            assertThatThrownBy { matchService.createMatch(userId, createRequest(players = listOf(player(userId = userId), player(userId = otherUserId)))) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)
                .hasMessageContaining(otherUserId.toString())

            verify(exactly = 0) { matchPlayerRepository.saveAll(any<Iterable<MatchPlayer>>()) }
        }

        @Test
        fun `should not evaluate achievements when saving the players fails`() {
            every { matchPlayerRepository.saveAll(any<Iterable<MatchPlayer>>()) } throws RuntimeException("Save failed")

            assertThatThrownBy { matchService.createMatch(userId, createRequest()) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Save failed")

            verifyNoAchievementEvaluation()
        }
    }

    @Nested
    @DisplayName("request validation")
    inner class ValidationTests {

        private fun assertRejected(request: CreateMatchRequest, type: Class<out Throwable>) {
            assertThatThrownBy { matchService.createMatch(userId, request) }.isInstanceOf(type)

            verify(exactly = 0) { gameRepository.findById(any()) }
            verifyNothingPersisted()
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should require at least one team for a team based match`() {
            assertRejected(createRequest(isTeamBased = true, players = null, teams = emptyList()), InvalidMatchTeamsException::class.java)
        }

        @Test
        fun `should reject individual players in a team based match`() {
            assertRejected(
                createRequest(isTeamBased = true, teams = listOf(team("A", 0, false, listOf(identity(userId = userId)))), players = listOf(player(userId = otherUserId))),
                InvalidMatchPlayersException::class.java,
            )
        }

        @Test
        fun `should reject a team without players`() {
            assertRejected(
                createRequest(isTeamBased = true, players = null, teams = listOf(team("A", 0, false, listOf(identity(userId = userId))), team("B", 0, false, emptyList()))),
                InvalidMatchTeamsException::class.java,
            )
        }

        @Test
        fun `should reject an invalid identity inside a team`() {
            assertRejected(
                createRequest(isTeamBased = true, players = null, teams = listOf(team("A", 0, false, listOf(identity(userId = userId, guestName = "Luca"))))),
                InvalidMatchPlayerIdentityException::class.java,
            )
        }

        @Test
        fun `should require at least one player for an individual match`() {
            assertRejected(createRequest(players = emptyList()), InvalidMatchPlayersException::class.java)
        }

        @Test
        fun `should reject teams in an individual match`() {
            assertRejected(createRequest(teams = listOf(team("A", 0, false, emptyList()))), InvalidMatchTeamsException::class.java)
        }

        @Test
        fun `should reject a player with both user and guest name`() {
            assertRejected(createRequest(players = listOf(player(userId = userId, guestName = "Luca"))), InvalidMatchPlayerIdentityException::class.java)
        }

        @Test
        fun `should reject a player with neither user nor guest name`() {
            assertRejected(createRequest(players = listOf(player())), InvalidMatchPlayerIdentityException::class.java)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   ", " \t \n "])
        fun `should reject a guest with a blank name`(blankName: String) {
            assertRejected(createRequest(players = listOf(player(guestName = blankName))), InvalidMatchPlayerIdentityException::class.java)
        }

        @Test
        fun `should reject the same user twice in an individual match`() {
            assertRejected(createRequest(players = listOf(player(userId = userId), player(userId = userId))), InvalidMatchPlayersException::class.java)
        }

        @Test
        fun `should reject the same user in two different teams`() {
            assertRejected(
                createRequest(isTeamBased = true, players = null, teams = listOf(team("A", 0, false, listOf(identity(userId = userId))), team("B", 0, false, listOf(identity(userId = userId))))),
                InvalidMatchPlayersException::class.java,
            )
        }

        @Test
        fun `should allow two guests with the same name`() {
            matchService.createMatch(userId, createRequest(players = listOf(player(guestName = "Luca"), player(guestName = "Luca"))))

            assertThat(savedPlayers).hasSize(2)
        }
    }

    @Nested
    @DisplayName("updateMatch")
    inner class UpdateMatchTests {

        @Test
        fun `should update the match fields`() {
            val match = existingMatch()

            matchService.updateMatch(userId, matchId, updateRequest(durationMinutes = 90))

            assertThat(match.place).isEqualTo("Club")
            assertThat(match.notes).isEqualTo("Rivincita")
            assertThat(match.durationMinutes).isEqualTo(90)
            assertThat(match.winnerScore).isEqualTo(10)
            verify(exactly = 0) { matchRepository.save(any<Match>()) }
        }

        @Test
        fun `should replace the players deleting the old ones first`() {
            existingMatch()

            matchService.updateMatch(userId, matchId, updateRequest(players = listOf(player(userId = userId), player(guestName = "Luca"))))

            verifyOrder {
                matchPlayerRepository.deleteAllByMatchId(matchId)
                matchTeamRepository.deleteAllByMatchId(matchId)
                matchPlayerRepository.saveAll(any<Iterable<MatchPlayer>>())
            }
            assertThat(savedPlayers.map { it.guestName }).containsExactly(null, "Luca")
        }

        @Test
        fun `should keep the original time when the date does not change`() {
            val playedAt = Instant.parse("2026-03-15T18:30:00Z")
            val match = existingMatch(playedAt = playedAt)

            matchService.updateMatch(userId, matchId, updateRequest(playedAt = LocalDate.of(2026, 3, 15)))

            assertThat(match.playedAt).isEqualTo(playedAt)
        }

        @Test
        fun `should move the match to the new date`() {
            val match = existingMatch(playedAt = Instant.parse("2026-03-15T18:30:00Z"))

            matchService.updateMatch(userId, matchId, updateRequest(playedAt = LocalDate.of(2026, 3, 20)))

            assertThat(match.playedAt.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 20))
        }

        @Test
        fun `should compute the duration from the creation time when closing a match in progress`() {
            val match = existingMatch(durationMinutes = null, createdAt = Instant.now().minusSeconds(3600))

            matchService.updateMatch(userId, matchId, updateRequest())

            assertThat(match.durationMinutes).isBetween(60, 61)
            assertThat(match.winnerScore).isEqualTo(10)
        }

        @Test
        fun `should use at least one minute when closing a match just created`() {
            val match = existingMatch(durationMinutes = null, createdAt = Instant.now())

            matchService.updateMatch(userId, matchId, updateRequest())

            assertThat(match.durationMinutes).isEqualTo(1)
        }

        @Test
        fun `should keep the existing duration when none is sent`() {
            val match = existingMatch(durationMinutes = 45)

            matchService.updateMatch(userId, matchId, updateRequest(durationMinutes = null))

            assertThat(match.durationMinutes).isEqualTo(45)
        }

        @Test
        fun `should evaluate achievements for the registered participants`() {
            existingMatch()

            matchService.updateMatch(userId, matchId, updateRequest(players = listOf(player(userId = userId), player(userId = otherUserId), player(guestName = "Luca"))))

            verify(exactly = 1) { userAchievementService.evaluateAfterCommit(setOf(userId, otherUserId)) }
        }

        @Test
        fun `should update the expansions used`() {
            val match = existingMatch()

            matchService.updateMatch(userId, matchId, updateRequest(expansionIds = listOf(expansionId)))

            assertThat(match.expansionsUsed).containsExactly(expansion)
        }

        @Test
        fun `should throw MatchNotFoundException when the match does not exist`() {
            every { matchRepository.findById(matchId) } returns Optional.empty()

            assertThatThrownBy { matchService.updateMatch(userId, matchId, updateRequest()) }
                .isInstanceOf(MatchNotFoundException::class.java)

            verify(exactly = 0) { matchPlayerRepository.deleteAllByMatchId(any()) }
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should throw NotMatchCreatorException when the user did not create the match`() {
            existingMatch(createdBy = otherUser)

            assertThatThrownBy { matchService.updateMatch(userId, matchId, updateRequest()) }
                .isInstanceOf(NotMatchCreatorException::class.java)

            verify(exactly = 0) { matchPlayerRepository.deleteAllByMatchId(any()) }
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should validate the payload before touching the players`() {
            existingMatch()

            assertThatThrownBy { matchService.updateMatch(userId, matchId, updateRequest(players = emptyList())) }
                .isInstanceOf(InvalidMatchPlayersException::class.java)

            verify(exactly = 0) { matchPlayerRepository.deleteAllByMatchId(any()) }
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should throw GameNotFoundException when the new game does not exist`() {
            existingMatch()
            every { gameRepository.findById(gameId) } returns Optional.empty()

            assertThatThrownBy { matchService.updateMatch(userId, matchId, updateRequest()) }
                .isInstanceOf(GameNotFoundException::class.java)

            verify(exactly = 0) { matchPlayerRepository.deleteAllByMatchId(any()) }
        }

        @Test
        fun `should throw InvalidExpansionForGameException before deleting the players`() {
            existingMatch()
            every { gameRepository.findById(gameId) } returns Optional.of(Game(id = gameId, bggId = 13L, name = "Catan", expansionRefs = emptyList()))

            assertThatThrownBy { matchService.updateMatch(userId, matchId, updateRequest(expansionIds = listOf(expansionId))) }
                .isInstanceOf(InvalidExpansionForGameException::class.java)

            verify(exactly = 0) { matchPlayerRepository.deleteAllByMatchId(any()) }
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when a participant deleted the account`() {
            existingMatch()
            otherUser.accountStatus = AccountStatus.DELETED

            assertThatThrownBy { matchService.updateMatch(userId, matchId, updateRequest(players = listOf(player(userId = userId), player(userId = otherUserId)))) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should not evaluate achievements when saving the players fails`() {
            existingMatch()
            every { matchPlayerRepository.saveAll(any<Iterable<MatchPlayer>>()) } throws RuntimeException("Save failed")

            assertThatThrownBy { matchService.updateMatch(userId, matchId, updateRequest()) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Save failed")

            verifyNoAchievementEvaluation()
        }
    }

    @Nested
    @DisplayName("deleteMatch")
    inner class DeleteMatchTests {

        @Test
        fun `should delete the match`() {
            val match = existingMatch()

            matchService.deleteMatch(userId, matchId)

            verify(exactly = 1) { matchRepository.delete(match) }
            verifyNoAchievementEvaluation()
        }

        @Test
        fun `should throw NotMatchCreatorException when the user did not create the match`() {
            existingMatch(createdBy = otherUser)

            assertThatThrownBy { matchService.deleteMatch(userId, matchId) }
                .isInstanceOf(NotMatchCreatorException::class.java)

            verify(exactly = 0) { matchRepository.delete(any<Match>()) }
        }

        @Test
        fun `should throw MatchNotFoundException when the match does not exist`() {
            every { matchRepository.findById(matchId) } returns Optional.empty()

            assertThatThrownBy { matchService.deleteMatch(userId, matchId) }
                .isInstanceOf(MatchNotFoundException::class.java)

            verify(exactly = 0) { matchRepository.delete(any<Match>()) }
        }
    }

    @Nested
    @DisplayName("getMatch")
    inner class GetMatchTests {

        @Test
        fun `should map an individual match with its players`() {
            val match = existingMatch(createdBy = otherUser)
            val players = listOf(
                MatchPlayer(id = UUID.randomUUID(), match = match, user = user, color = "Red", score = 10, isWinner = true, isStartingFirst = true),
                MatchPlayer(id = UUID.randomUUID(), match = match, guestName = "Luca", color = "Blue", score = 4, isWinner = false, isStartingFirst = false),
            )
            every { matchPlayerRepository.findAllByMatchIdIn(listOf(matchId)) } returns players
            every { matchTeamRepository.findAllByMatchIdIn(listOf(matchId)) } returns emptyList()

            val result = matchService.getMatch(matchId)

            assertThat(result.id).isEqualTo(matchId)
            assertThat(result.isTeamBased).isFalse()
            assertThat(result.players).hasSize(2)
            assertThat(result.teams).isNull()
        }

        @Test
        fun `should map a team match grouping players by team`() {
            val match = existingMatch(isTeamBased = true)
            val teamA = MatchTeam(id = UUID.randomUUID(), match = match, name = "A", color = "Red", score = 10, isWinner = true, isStartingFirst = true)
            val teamB = MatchTeam(id = UUID.randomUUID(), match = match, name = "B", color = "Blue", score = 5, isWinner = false, isStartingFirst = false)
            val players = listOf(
                MatchPlayer(id = UUID.randomUUID(), match = match, team = teamA, user = user),
                MatchPlayer(id = UUID.randomUUID(), match = match, team = teamB, user = otherUser),
                MatchPlayer(id = UUID.randomUUID(), match = match, team = teamB, guestName = "Luca"),
            )
            every { matchPlayerRepository.findAllByMatchIdIn(listOf(matchId)) } returns players
            every { matchTeamRepository.findAllByMatchIdIn(listOf(matchId)) } returns listOf(teamA, teamB)

            val result = matchService.getMatch(matchId)

            assertThat(result.isTeamBased).isTrue()
            assertThat(result.players).isNull()
            assertThat(result.teams!!.map { it.players.size }).containsExactly(1, 2)
        }

        @Test
        fun `should return a match still in progress`() {
            existingMatch(durationMinutes = null)
            every { matchPlayerRepository.findAllByMatchIdIn(listOf(matchId)) } returns emptyList()
            every { matchTeamRepository.findAllByMatchIdIn(listOf(matchId)) } returns emptyList()

            assertThat(matchService.getMatch(matchId).durationMinutes).isNull()
        }

        @Test
        fun `should throw MatchNotFoundException when the match does not exist`() {
            every { matchRepository.findById(matchId) } returns Optional.empty()

            assertThatThrownBy { matchService.getMatch(matchId) }
                .isInstanceOf(MatchNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("listMyMatches")
    inner class ListMyMatchesTests {

        private val pageableSlot = slot<Pageable>()

        private fun stubPage(matches: List<Match> = emptyList()) {
            every { matchRepository.findAll(any<Specification<Match>>(), capture(pageableSlot)) } answers { PageImpl(matches, secondArg(), matches.size.toLong()) }
            every { matchPlayerRepository.findAllByMatchIdIn(any()) } returns emptyList()
            every { matchTeamRepository.findAllByMatchIdIn(any()) } returns emptyList()
        }

        private fun ordersFor(sort: String?): List<Sort.Order> {
            stubPage()
            matchService.listMyMatches(userId, 0, 20, null, null, null, sort)
            return pageableSlot.captured.sort.toList()
        }

        @Test
        fun `should map the page content keeping the repository order`() {
            val first = Match(id = UUID.randomUUID(), game = game, createdBy = user, playedAt = Instant.now(), durationMinutes = 30)
            val second = Match(id = UUID.randomUUID(), game = game, createdBy = user, playedAt = Instant.now(), durationMinutes = 60)
            stubPage(listOf(first, second))

            val result = matchService.listMyMatches(userId, 0, 20, null, null, null, null)

            assertThat(result.content.map { it.id }).containsExactly(first.id, second.id)
            verify(exactly = 1) { matchPlayerRepository.findAllByMatchIdIn(listOf(first.id!!, second.id!!)) }
        }

        @Test
        fun `should not load players for an empty page`() {
            stubPage()

            val result = matchService.listMyMatches(userId, 0, 20, null, null, null, null)

            assertThat(result.content).isEmpty()
            verify(exactly = 0) { matchPlayerRepository.findAllByMatchIdIn(any()) }
        }

        @Test
        fun `should clamp the page and the size`() {
            stubPage()

            matchService.listMyMatches(userId, -5, 150, null, null, null, null)

            assertThat(pageableSlot.captured.pageNumber).isZero()
            assertThat(pageableSlot.captured.pageSize).isEqualTo(100)
        }

        @Test
        fun `should sort by playedAt desc then createdAt desc by default`() {
            val orders = ordersFor(null)

            assertThat(orders.map { it.property }).containsExactly("playedAt", "createdAt")
            assertThat(orders).allMatch { it.direction == Sort.Direction.DESC }
        }

        @Test
        fun `should sort by playedAt asc keeping createdAt desc as tie-breaker`() {
            val orders = ordersFor("playedAt-asc")

            assertThat(orders.map { it.property }).containsExactly("playedAt", "createdAt")
            assertThat(orders.map { it.direction }).containsExactly(Sort.Direction.ASC, Sort.Direction.DESC)
        }

        @ParameterizedTest
        @ValueSource(strings = ["durationMinutes-asc", "durationMinutes-desc", "winnerScore-asc", "winnerScore-desc"])
        fun `should put nulls last for optional fields`(sort: String) {
            val orders = ordersFor(sort)

            assertThat(orders[0].property).isEqualTo(sort.substringBefore("-"))
            assertThat(orders[0].nullHandling).isEqualTo(Sort.NullHandling.NULLS_LAST)
            assertThat(orders.drop(1).map { it.property }).containsExactly("playedAt", "createdAt")
        }

        @Test
        fun `should throw InvalidSortException without querying for a field that is not allowed`() {
            assertThatThrownBy { matchService.listMyMatches(userId, 0, 20, null, null, null, "place-asc") }
                .isInstanceOf(InvalidSortException::class.java)

            verify(exactly = 0) { matchRepository.findAll(any<Specification<Match>>(), any<Pageable>()) }
        }
    }

    @Nested
    @DisplayName("getMatchCalendar")
    inner class GetMatchCalendarTests {

        private val fromSlot = slot<Instant>()
        private val toSlot = slot<Instant>()

        private fun projection(date: LocalDate, count: Long): MatchDayCountProjection = mockk {
            every { playedAt } returns date
            every { matchCount } returns count
        }

        @Test
        fun `should return the match count of each day`() {
            every { matchRepository.countMatchesByDay(userId, any(), any()) } returns listOf(projection(LocalDate.of(2026, 3, 15), 3L), projection(LocalDate.of(2026, 3, 20), 1L))

            val result = matchService.getMatchCalendar(userId, 2026, 3)

            assertThat(result.map { it.date }).containsExactly(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 20))
            assertThat(result.map { it.count }).containsExactly(3L, 1L)
        }

        @Test
        fun `should query from the first day of the month to the first day of the next one`() {
            every { matchRepository.countMatchesByDay(userId, capture(fromSlot), capture(toSlot)) } returns emptyList()

            matchService.getMatchCalendar(userId, 2026, 3)

            assertThat(fromSlot.captured).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"))
            assertThat(toSlot.captured).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"))
        }

        @Test
        fun `should roll over to the next year for december`() {
            every { matchRepository.countMatchesByDay(userId, capture(fromSlot), capture(toSlot)) } returns emptyList()

            matchService.getMatchCalendar(userId, 2026, 12)

            assertThat(fromSlot.captured).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"))
            assertThat(toSlot.captured).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"))
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 13])
        fun `should reject an invalid month without querying`(month: Int) {
            assertThatThrownBy { matchService.getMatchCalendar(userId, 2026, month) }
                .isInstanceOf(DateTimeException::class.java)

            verify(exactly = 0) { matchRepository.countMatchesByDay(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("getRecentGames")
    inner class GetRecentGamesTests {

        private val pageableSlot = slot<Pageable>()

        @Test
        fun `should return distinct games keeping the most recent order`() {
            val arkNova = Game(id = UUID.randomUUID(), bggId = 342942L, name = "Ark Nova")
            val matches = listOf(game, game, arkNova, game).map { Match(id = UUID.randomUUID(), game = it, createdBy = user, playedAt = Instant.now()) }
            every { matchRepository.findRecentForUser(userId, any()) } returns matches

            val result = matchService.getRecentGames(userId)

            assertThat(result.map { it.id }).containsExactly(gameId, arkNova.id)
        }

        @Test
        fun `should look at the requested number of matches`() {
            every { matchRepository.findRecentForUser(userId, capture(pageableSlot)) } returns emptyList()

            matchService.getRecentGames(userId, 5)

            assertThat(pageableSlot.captured.pageNumber).isZero()
            assertThat(pageableSlot.captured.pageSize).isEqualTo(5)
        }

        @Test
        fun `should return an empty list when the user has no matches`() {
            every { matchRepository.findRecentForUser(userId, any()) } returns emptyList()

            assertThat(matchService.getRecentGames(userId)).isEmpty()
        }
    }

    @Nested
    @DisplayName("getWinStats")
    inner class GetWinStatsTests {

        @Test
        fun `should return completed matches and wins`() {
            every { matchRepository.countCompletedMatchesForUser(userId) } returns 10L
            every { matchRepository.countWonMatchesForUser(userId) } returns 4L

            val result = matchService.getWinStats(userId)

            assertThat(result.totalMatches).isEqualTo(10L)
            assertThat(result.totalWins).isEqualTo(4L)
        }

        @Test
        fun `should propagate a repository failure`() {
            every { matchRepository.countCompletedMatchesForUser(userId) } throws RuntimeException("Database error")

            assertThatThrownBy { matchService.getWinStats(userId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Database error")
        }
    }

    @Nested
    @DisplayName("getRecentMatchesForUser")
    inner class GetRecentMatchesForUserTests {

        private val pageableSlot = slot<Pageable>()

        @Test
        fun `should map the recent completed matches`() {
            val match = Match(id = matchId, game = game, createdBy = user, playedAt = Instant.now(), durationMinutes = 45)
            every { matchRepository.findRecentCompletedForUser(userId, capture(pageableSlot)) } returns listOf(match)
            every { matchPlayerRepository.findAllByMatchIdIn(listOf(matchId)) } returns emptyList()
            every { matchTeamRepository.findAllByMatchIdIn(listOf(matchId)) } returns emptyList()

            val result = matchService.getRecentMatchesForUser(userId, 3)

            assertThat(result.map { it.id }).containsExactly(matchId)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(3)
        }

        @Test
        fun `should not load players when there are no matches`() {
            every { matchRepository.findRecentCompletedForUser(userId, any()) } returns emptyList()

            assertThat(matchService.getRecentMatchesForUser(userId)).isEmpty()

            verify(exactly = 0) { matchPlayerRepository.findAllByMatchIdIn(any()) }
            verify(exactly = 0) { matchTeamRepository.findAllByMatchIdIn(any()) }
        }

        @Test
        fun `should propagate a repository failure`() {
            every { matchRepository.findRecentCompletedForUser(userId, any()) } throws RuntimeException("Database error")

            assertThatThrownBy { matchService.getRecentMatchesForUser(userId) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }

    @Nested
    @DisplayName("listGameStats")
    inner class ListGameStatsTests {

        private val pageableSlot = slot<Pageable>()

        private fun projection(): GameMatchStatsProjection = mockk {
            every { gameId } returns this@MatchServiceTest.gameId
            every { bggId } returns 13L
            every { gameName } returns "Catan"
            every { gameThumbnailUrl } returns "https://example.com/thumb.jpg"
            every { matchCount } returns 5L
            every { totalMinutes } returns 300L
            every { avgMinutes } returns 60.0
            every { wins } returns 2L
            every { winRate } returns 0.4
            every { lastPlayedAt } returns Instant.parse("2026-10-01T10:00:00Z")
        }

        private fun capturePageable(page: Int = 0, size: Int = 20, sort: String? = null): Pageable {
            every { matchRepository.findGameStatsForUser(userId, capture(pageableSlot)) } answers { PageImpl(emptyList(), secondArg(), 0) }
            matchService.listGameStats(userId, page, size, sort)
            return pageableSlot.captured
        }

        @Test
        fun `should map the projections to DTOs`() {
            every { matchRepository.findGameStatsForUser(userId, any()) } returns PageImpl(listOf(projection()), PageRequest.of(0, 20), 1)

            val dto = matchService.listGameStats(userId, 0, 20, null).content.single()

            assertThat(dto.gameId).isEqualTo(gameId)
            assertThat(dto.bggId).isEqualTo(13L)
            assertThat(dto.name).isEqualTo("Catan")
            assertThat(dto.matchCount).isEqualTo(5L)
            assertThat(dto.totalMinutes).isEqualTo(300L)
            assertThat(dto.avgMinutes).isEqualTo(60.0)
            assertThat(dto.wins).isEqualTo(2L)
            assertThat(dto.winRate).isEqualTo(0.4)
        }

        @Test
        fun `should sort by matchCount desc then gameName asc by default`() {
            val orders = capturePageable().sort.toList()

            assertThat(orders.map { it.property }).containsExactly("matchCount", "gameName")
            assertThat(orders.map { it.direction }).containsExactly(Sort.Direction.DESC, Sort.Direction.ASC)
        }

        @Test
        fun `should sort by the requested field`() {
            val orders = capturePageable(sort = "totalMinutes-asc").sort.toList()

            assertThat(orders.map { it.property }).containsExactly("totalMinutes", "gameName")
            assertThat(orders[0].direction).isEqualTo(Sort.Direction.ASC)
        }

        @Test
        fun `should not duplicate gameName when sorting by it`() {
            assertThat(capturePageable(sort = "gameName-desc").sort.toList().map { it.property }).containsExactly("gameName")
        }

        @Test
        fun `should clamp the page and the size`() {
            val pageable = capturePageable(page = -3, size = 500)

            assertThat(pageable.pageNumber).isZero()
            assertThat(pageable.pageSize).isEqualTo(100)
        }

        @Test
        fun `should raise a size below one to one`() {
            assertThat(capturePageable(size = 0).pageSize).isEqualTo(1)
        }

        @Test
        fun `should throw InvalidSortException without querying for a field that is not allowed`() {
            assertThatThrownBy { matchService.listGameStats(userId, 0, 20, "notAllowed-asc") }
                .isInstanceOf(InvalidSortException::class.java)

            verify(exactly = 0) { matchRepository.findGameStatsForUser(any(), any()) }
        }

        @Test
        fun `should propagate a repository failure`() {
            every { matchRepository.findGameStatsForUser(userId, any()) } throws RuntimeException("Database error")

            assertThatThrownBy { matchService.listGameStats(userId, 0, 20, null) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }
}