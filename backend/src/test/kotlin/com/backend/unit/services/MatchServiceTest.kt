package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.*
import com.backend.models.entities.*
import com.backend.models.projections.MatchDayCountProjection
import com.backend.repositories.*
import com.backend.services.MatchService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.time.LocalDate
import java.util.*

@ExtendWith(MockitoExtension::class)
class MatchServiceTest {

    @Mock
    private lateinit var matchRepository: MatchRepository

    @Mock
    private lateinit var matchTeamRepository: MatchTeamRepository

    @Mock
    private lateinit var matchPlayerRepository: MatchPlayerRepository

    @Mock
    private lateinit var gameRepository: GameRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @InjectMocks
    private lateinit var matchService: MatchService

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val gameId = UUID.randomUUID()
    private val matchId = UUID.randomUUID()

    private lateinit var sampleUser: User
    private lateinit var sampleGame: Game

    @BeforeEach
    fun setUp() {
        sampleUser = User(
            id = userId,
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hash"
        )

        sampleGame = Game(
            id = gameId,
            bggId = 12345L,
            name = "Catan",
            minPlayers = 2,
            maxPlayers = 4
        )
    }

    @Nested
    @DisplayName("createMatch")
    inner class CreateMatchTests {

        @Test
        fun `should create individual match successfully`() {
            val playerRequest = MatchIndividualPlayerRequest(
                userId = userId,
                guestName = null,
                color = "Red",
                score = 10,
                isWinner = true,
                startingPosition = 1
            )
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = "Home",
                notes = "Fun game",
                durationMinutes = 60,
                teams = null,
                players = listOf(playerRequest)
            )

            val createdMatch = Match(
                id = matchId,
                game = sampleGame,
                createdBy = sampleUser,
                isTeamBased = false,
                playedAt = request.playedAt,
                place = request.place,
                notes = request.notes,
                durationMinutes = 60
            )

            `when`(gameRepository.findById(gameId)).thenReturn(Optional.of(sampleGame))
            `when`(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser))
            `when`(matchRepository.save(any(Match::class.java))).thenReturn(createdMatch)

            doAnswer { invocation ->
                val mp = invocation.getArgument<MatchPlayer>(0)
                mp.id = UUID.randomUUID()
                mp
            }.`when`(matchPlayerRepository).save(any(MatchPlayer::class.java))

            val result = matchService.createMatch(userId, request)

            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(matchId)
            assertThat(result.isTeamBased).isFalse()
            assertThat(result.players).hasSize(1)
            verify(matchRepository).save(any(Match::class.java))
            verify(matchPlayerRepository).save(any(MatchPlayer::class.java))
        }

        @Test
        fun `should create team-based match successfully`() {
            val identityRequest = MatchPlayerIdentityRequest(userId = userId, guestName = null)
            val teamRequest = CreateMatchTeamRequest(
                name = "Team A",
                color = "Blue",
                score = 20,
                isWinner = true,
                startingPosition = 1,
                players = listOf(identityRequest)
            )
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = true,
                playedAt = LocalDate.now(),
                place = "Club",
                notes = "Tournament",
                durationMinutes = 120,
                teams = listOf(teamRequest),
                players = null
            )

            val createdMatch = Match(
                id = matchId,
                game = sampleGame,
                createdBy = sampleUser,
                isTeamBased = true,
                playedAt = request.playedAt,
                place = request.place,
                notes = request.notes,
                durationMinutes = 120
            )

            val createdTeam = MatchTeam(
                id = UUID.randomUUID(),
                match = createdMatch,
                name = "Team A",
                color = "Blue",
                score = 20,
                isWinner = true,
                startingPosition = 1
            )

            `when`(gameRepository.findById(gameId)).thenReturn(Optional.of(sampleGame))
            `when`(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser))
            `when`(matchRepository.save(any(Match::class.java))).thenReturn(createdMatch)
            `when`(matchTeamRepository.save(any(MatchTeam::class.java))).thenReturn(createdTeam)

            doAnswer { invocation ->
                val mp = invocation.getArgument<MatchPlayer>(0)
                mp.id = UUID.randomUUID()
                mp
            }.`when`(matchPlayerRepository).save(any(MatchPlayer::class.java))

            val result = matchService.createMatch(userId, request)

            assertThat(result).isNotNull
            assertThat(result.isTeamBased).isTrue()
            assertThat(result.teams).hasSize(1)
            verify(matchTeamRepository).save(any(MatchTeam::class.java))
        }

        @Test
        fun `should throw GameNotFoundException when game does not exist`() {
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = "Home",
                notes = null,
                durationMinutes = 30,
                teams = null,
                players = listOf(MatchIndividualPlayerRequest(userId, null, "Red", 0, false, 1))
            )

            `when`(gameRepository.findById(gameId)).thenReturn(Optional.empty())

            assertThatThrownBy { matchService.createMatch(userId, request) }
                .isInstanceOf(GameNotFoundException::class.java)
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when creator does not exist`() {
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = "Home",
                notes = null,
                durationMinutes = 30,
                teams = null,
                players = listOf(MatchIndividualPlayerRequest(userId, null, "Red", 0, false, 1))
            )

            `when`(gameRepository.findById(gameId)).thenReturn(Optional.of(sampleGame))
            `when`(userRepository.findById(userId)).thenReturn(Optional.empty())

            assertThatThrownBy { matchService.createMatch(userId, request) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)
        }
    }

    @Nested
    @DisplayName("Request Validation Tests")
    inner class ValidationTests {

        @Test
        fun `should throw InvalidMatchTeamsException when team based but teams empty`() {
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = true,
                playedAt = LocalDate.now(),
                place = null,
                notes = null,
                durationMinutes = null,
                teams = emptyList(),
                players = null
            )

            assertThatThrownBy { matchService.createMatch(userId, request) }
                .isInstanceOf(InvalidMatchTeamsException::class.java)
        }

        @Test
        fun `should throw InvalidMatchPlayersException when team based but individual players provided`() {
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = true,
                playedAt = LocalDate.now(),
                place = null,
                notes = null,
                durationMinutes = null,
                teams = listOf(CreateMatchTeamRequest("Team", "Red", 0, false, 1, listOf(MatchPlayerIdentityRequest(userId, null)))),
                players = listOf(MatchIndividualPlayerRequest(userId, null, "Blue", 0, false, 2))
            )

            assertThatThrownBy { matchService.createMatch(userId, request) }
                .isInstanceOf(InvalidMatchPlayersException::class.java)
        }

        @Test
        fun `should throw InvalidMatchPlayersException when individual based but players empty`() {
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = null,
                notes = null,
                durationMinutes = null,
                teams = null,
                players = emptyList()
            )

            assertThatThrownBy { matchService.createMatch(userId, request) }
                .isInstanceOf(InvalidMatchPlayersException::class.java)
        }

        @Test
        fun `should throw InvalidMatchTeamsException when individual based but teams provided`() {
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = null,
                notes = null,
                durationMinutes = null,
                teams = listOf(CreateMatchTeamRequest("Team", "Red", 0, false, 1, emptyList())),
                players = listOf(MatchIndividualPlayerRequest(userId, null, "Blue", 0, false, 2))
            )

            assertThatThrownBy { matchService.createMatch(userId, request) }
                .isInstanceOf(InvalidMatchTeamsException::class.java)
        }

        @Test
        fun `should throw InvalidMatchPlayerIdentityException when both userId and guestName are provided`() {
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = null,
                notes = null,
                durationMinutes = null,
                teams = null,
                players = listOf(MatchIndividualPlayerRequest(userId, "GuestJohn", "Red", 0, false, 1))
            )

            assertThatThrownBy { matchService.createMatch(userId, request) }
                .isInstanceOf(InvalidMatchPlayerIdentityException::class.java)
        }

        @Test
        fun `should throw InvalidMatchPlayerIdentityException when neither userId nor guestName are provided`() {
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = null,
                notes = null,
                durationMinutes = null,
                teams = null,
                players = listOf(MatchIndividualPlayerRequest(null, null, "Red", 0, false, 1))
            )

            assertThatThrownBy { matchService.createMatch(userId, request) }
                .isInstanceOf(InvalidMatchPlayerIdentityException::class.java)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   ", " \t \n "])
        fun `should throw InvalidMatchPlayerIdentityException when guestName is blank and userId is null`(blankName: String) {
            val request = CreateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = null,
                notes = null,
                durationMinutes = null,
                teams = null,
                players = listOf(MatchIndividualPlayerRequest(null, blankName, "Red", 0, false, 1))
            )

            assertThatThrownBy { matchService.createMatch(userId, request) }
                .isInstanceOf(InvalidMatchPlayerIdentityException::class.java)
        }
    }

    @Nested
    @DisplayName("updateMatch")
    inner class UpdateMatchTests {

        @Test
        fun `should update match and calculate duration when durationMinutes is null`() {
            val existingMatch = Match(
                id = matchId,
                game = sampleGame,
                createdBy = sampleUser,
                isTeamBased = false,
                playedAt = LocalDate.now().minusDays(1),
                createdAt = Instant.now().minusSeconds(3600),
                durationMinutes = null
            )

            val updateRequest = UpdateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = "Updated Place",
                notes = "Updated Notes",
                teams = null,
                players = listOf(MatchIndividualPlayerRequest(userId, null, "Blue", 15, true, 1))
            )

            `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(existingMatch))
            `when`(gameRepository.findById(gameId)).thenReturn(Optional.of(sampleGame))
            `when`(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser))
            `when`(matchRepository.save(any(Match::class.java))).thenAnswer { it.arguments[0] }

            doAnswer { invocation ->
                val mp = invocation.getArgument<MatchPlayer>(0)
                mp.id = UUID.randomUUID()
                mp
            }.`when`(matchPlayerRepository).save(any(MatchPlayer::class.java))

            val result = matchService.updateMatch(userId, matchId, updateRequest)

            assertThat(result).isNotNull
            verify(matchPlayerRepository).deleteAllByMatchId(matchId)
            verify(matchTeamRepository).deleteAllByMatchId(matchId)
            assertThat(existingMatch.durationMinutes).isGreaterThanOrEqualTo(60)
        }

        @Test
        fun `should throw NotMatchCreatorException when updating match created by another user`() {
            val existingMatch = Match(
                id = matchId,
                game = sampleGame,
                createdBy = sampleUser,
                isTeamBased = false,
                playedAt = LocalDate.now()
            )

            val updateRequest = UpdateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = null,
                notes = null,
                teams = null,
                players = listOf(MatchIndividualPlayerRequest(userId, null, "Red", 0, false, 1))
            )

            `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(existingMatch))

            assertThatThrownBy { matchService.updateMatch(otherUserId, matchId, updateRequest) }
                .isInstanceOf(NotMatchCreatorException::class.java)
        }

        @Test
        fun `should throw MatchNotFoundException when updating non-existent match`() {
            val updateRequest = UpdateMatchRequest(
                gameId = gameId,
                isTeamBased = false,
                playedAt = LocalDate.now(),
                place = null,
                notes = null,
                teams = null,
                players = listOf(MatchIndividualPlayerRequest(userId, null, "Red", 0, false, 1))
            )

            `when`(matchRepository.findById(matchId)).thenReturn(Optional.empty())

            assertThatThrownBy { matchService.updateMatch(userId, matchId, updateRequest) }
                .isInstanceOf(MatchNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("deleteMatch")
    inner class DeleteMatchTests {

        @Test
        fun `should delete match successfully`() {
            val match = Match(id = matchId, game = sampleGame, createdBy = sampleUser, playedAt = LocalDate.now())

            `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(match))

            matchService.deleteMatch(userId, matchId)

            verify(matchRepository).delete(match)
        }

        @Test
        fun `should throw NotMatchCreatorException when deleting match created by another user`() {
            val match = Match(id = matchId, game = sampleGame, createdBy = sampleUser, playedAt = LocalDate.now())

            `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(match))

            assertThatThrownBy { matchService.deleteMatch(otherUserId, matchId) }
                .isInstanceOf(NotMatchCreatorException::class.java)

            verify(matchRepository, never()).delete(any(Match::class.java))
        }

        @Test
        fun `should throw MatchNotFoundException when deleting non-existent match`() {
            `when`(matchRepository.findById(matchId)).thenReturn(Optional.empty())

            assertThatThrownBy { matchService.deleteMatch(userId, matchId) }
                .isInstanceOf(MatchNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("getMatch")
    inner class GetMatchTests {

        @Test
        fun `should get completed match when requested by non-creator`() {
            val match = Match(
                id = matchId,
                game = sampleGame,
                createdBy = sampleUser,
                playedAt = LocalDate.now(),
                durationMinutes = 45,
                isTeamBased = false
            )

            `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(match))
            `when`(matchPlayerRepository.findAllByMatchId(matchId)).thenReturn(emptyList())

            val result = matchService.getMatch(matchId, otherUserId)

            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(matchId)
        }

        @Test
        fun `should throw NotMatchCreatorException when in-progress match requested by non-creator`() {
            val match = Match(
                id = matchId,
                game = sampleGame,
                createdBy = sampleUser,
                playedAt = LocalDate.now(),
                durationMinutes = null
            )

            `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(match))

            assertThatThrownBy { matchService.getMatch(matchId, otherUserId) }
                .isInstanceOf(NotMatchCreatorException::class.java)
        }

        @Test
        fun `should retrieve team-based match with mapped team and player responses`() {
            val match = Match(
                id = matchId,
                game = sampleGame,
                createdBy = sampleUser,
                playedAt = LocalDate.now(),
                durationMinutes = 60,
                isTeamBased = true
            )
            val teamId = UUID.randomUUID()
            val team = MatchTeam(
                id = teamId,
                match = match,
                name = "Red Team",
                color = "Red",
                score = 10,
                isWinner = true,
                startingPosition = 1
            )
            val player = MatchPlayer(
                id = UUID.randomUUID(),
                match = match,
                team = team,
                user = sampleUser,
                guestName = null,
                color = "Red",
                score = 10,
                isWinner = true,
                startingPosition = 1
            )

            `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(match))
            `when`(matchTeamRepository.findAllByMatchId(matchId)).thenReturn(listOf(team))
            `when`(matchPlayerRepository.findAllByMatchId(matchId)).thenReturn(listOf(player))

            val result = matchService.getMatch(matchId, userId)

            assertThat(result).isNotNull
            assertThat(result.isTeamBased).isTrue()
            assertThat(result.teams).hasSize(1)
            assertThat(result.teams!![0].players).hasSize(1)
        }
    }

    @Nested
    @DisplayName("listMyMatches")
    inner class ListMyMatchesTests {

        @Test
        fun `should sanitize negative page and limit size within range`() {
            val pageableCaptor = ArgumentCaptor.forClass(Pageable::class.java)
            val match = Match(
                id = matchId,
                game = sampleGame,
                createdBy = sampleUser,
                playedAt = LocalDate.now(),
                durationMinutes = 30,
                isTeamBased = false
            )

            `when`(matchRepository.findAll(any(), pageableCaptor.capture())).thenAnswer { invocation ->
                val pageable = invocation.getArgument<Pageable>(1)
                PageImpl(listOf(match), pageable, 1)
            }

            val result = matchService.listMyMatches(
                userId = userId,
                page = -5,
                size = 150,
                gameId = null,
                fromDate = null,
                toDate = null,
                sort = "playedAt"
            )

            assertThat(result).isNotNull

            val capturedPageable = pageableCaptor.value
            assertThat(capturedPageable.pageNumber).isEqualTo(0)
            assertThat(capturedPageable.pageSize).isEqualTo(100)
        }

        @Test
        fun `should throw InvalidSortException when invalid sort parameter provided`() {
            assertThatThrownBy {
                matchService.listMyMatches(userId, page = 0, size = 10, gameId = null, fromDate = null, toDate = null, sort = "invalidField")
            }.isInstanceOf(InvalidSortException::class.java)
        }
    }

    @Nested
    @DisplayName("getMatchCalendar")
    inner class GetMatchCalendarTests {

        @Test
        fun `should return counts per day for month`() {
            val year = 2026
            val month = 3
            val fromDate = LocalDate.of(2026, 3, 1)
            val toDate = LocalDate.of(2026, 3, 31)

            val dayProjection = mock(MatchDayCountProjection::class.java)
            `when`(dayProjection.playedAt).thenReturn(LocalDate.of(2026, 3, 15))
            `when`(dayProjection.matchCount).thenReturn(3L)

            `when`(matchRepository.countMatchesByDay(userId, fromDate, toDate)).thenReturn(listOf(dayProjection))

            val result = matchService.getMatchCalendar(userId, year, month)

            assertThat(result).hasSize(1)
            assertThat(result[0].date).isEqualTo(LocalDate.of(2026, 3, 15))
            assertThat(result[0].count).isEqualTo(3L)
        }
    }
}