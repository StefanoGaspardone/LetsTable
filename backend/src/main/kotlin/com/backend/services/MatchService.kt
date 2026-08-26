package com.backend.services

import com.backend.exceptions.*
import com.backend.models.dtos.CreateMatchRequest
import com.backend.models.dtos.MatchDTO
import com.backend.models.dtos.MatchDayCountResponse
import com.backend.models.dtos.MatchIndividualPlayerRequest
import com.backend.models.dtos.MatchPlayerDTO
import com.backend.models.dtos.MatchPlayerIdentityRequest
import com.backend.models.dtos.MatchPlayerRefDTO
import com.backend.models.dtos.MatchTeamDTO
import com.backend.models.dtos.PageDTO
import com.backend.models.entities.*
import com.backend.models.mappers.toPageDTO
import com.backend.models.specifications.MatchSpecification
import com.backend.repositories.*
import com.backend.utils.resolveSort
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class MatchService(
    private val matchRepository: MatchRepository,
    private val matchTeamRepository: MatchTeamRepository,
    private val matchPlayerRepository: MatchPlayerRepository,
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createMatch(userId: UUID, request: CreateMatchRequest): MatchDTO {
        logger.debug("\n\t[DEBUG] [match_service][create_match] User {} creating match for game {}", userId, request.gameId)

        try {
            validateRequest(request)

            val game = gameRepository.findById(request.gameId)
                .orElseThrow { GameNotFoundException(request.gameId) }
            val createdBy = userRepository.findById(userId)
                .orElseThrow { UserNotFoundByIdentifierException(userId.toString()) }

            val match = Match(
                game = game,
                createdBy = createdBy,
                isTeamBased = request.isTeamBased,
                playedAt = request.playedAt,
                place = request.place,
                notes = request.notes,
                durationMinutes = request.durationMinutes,
            )
            val savedMatch = matchRepository.save(match)

            val response = buildMatchPlayersAndTeams(savedMatch, request)

            logger.info("\n\t[INFO] [match_service][create_match] Match {} created by user {}", savedMatch.id, userId)
            return response
        } catch(e: InvalidMatchTeamsException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Invalid teams payload from user {}: {}", userId, e.message)
            throw e
        } catch(e: InvalidMatchPlayersException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Invalid players payload from user {}: {}", userId, e.message)
            throw e
        } catch(e: InvalidMatchPlayerIdentityException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Invalid player identity from user {}", userId)
            throw e
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Game {} not found", request.gameId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][create_match] Error creating match for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun updateMatch(userId: UUID, matchId: UUID, request: CreateMatchRequest): MatchDTO {
        logger.debug("\n\t[DEBUG] [match_service][update_match] User {} updating match {}", userId, matchId)

        try {
            val match = matchRepository.findById(matchId)
                .orElseThrow { MatchNotFoundException(matchId) }

            if(match.createdBy.id != userId) {
                throw NotMatchCreatorException()
            }

            validateRequest(request)

            val game = gameRepository.findById(request.gameId)
                .orElseThrow { GameNotFoundException(request.gameId) }

            match.game = game
            match.isTeamBased = request.isTeamBased
            match.playedAt = request.playedAt
            match.place = request.place
            match.notes = request.notes
            match.durationMinutes = request.durationMinutes
            val savedMatch = matchRepository.save(match)

            matchPlayerRepository.deleteAllByMatchId(matchId)
            matchTeamRepository.deleteAllByMatchId(matchId)

            val response = buildMatchPlayersAndTeams(savedMatch, request)

            logger.info("\n\t[INFO] [match_service][update_match] Match {} updated by user {}", matchId, userId)
            return response
        } catch(e: MatchNotFoundException) {
            logger.warn("\n\t[WARN] [match_service][update_match] Match {} not found", matchId)
            throw e
        } catch(e: NotMatchCreatorException) {
            logger.warn("\n\t[WARN] [match_service][update_match] User {} is not the creator of match {}", userId, matchId)
            throw e
        } catch(e: InvalidMatchTeamsException) {
            logger.warn("\n\t[WARN] [match_service][update_match] Invalid teams payload for match {}: {}", matchId, e.message)
            throw e
        } catch(e: InvalidMatchPlayersException) {
            logger.warn("\n\t[WARN] [match_service][update_match] Invalid players payload for match {}: {}", matchId, e.message)
            throw e
        } catch(e: InvalidMatchPlayerIdentityException) {
            logger.warn("\n\t[WARN] [match_service][update_match] Invalid player identity for match {}", matchId)
            throw e
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [match_service][update_match] Game {} not found", request.gameId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][update_match] Error updating match {}: {}", matchId, e.message)
            throw e
        }
    }

    @Transactional
    fun deleteMatch(userId: UUID, matchId: UUID) {
        logger.debug("\n\t[DEBUG] [match_service][delete_match] User {} deleting match {}", userId, matchId)

        try {
            val match = matchRepository.findById(matchId)
                .orElseThrow { MatchNotFoundException(matchId) }

            if(match.createdBy.id != userId) {
                throw NotMatchCreatorException()
            }

            matchRepository.delete(match)

            logger.info("\n\t[INFO] [match_service][delete_match] Match {} deleted by user {}", matchId, userId)
        } catch(e: MatchNotFoundException) {
            logger.warn("\n\t[WARN] [match_service][delete_match] Match {} not found", matchId)
            throw e
        } catch(e: NotMatchCreatorException) {
            logger.warn("\n\t[WARN] [match_service][delete_match] User {} is not the creator of match {}", userId, matchId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][delete_match] Error deleting match {}: {}", matchId, e.message)
            throw e
        }
    }

    @Transactional
    fun getMatch(matchId: UUID, userId: UUID): MatchDTO {
        logger.debug("\n\t[DEBUG] [match_service][get_match] Retrieving match {}", matchId)

        try {
            val match = matchRepository.findById(matchId)
                .orElseThrow { MatchNotFoundException(matchId) }

            if(match.durationMinutes == null && match.createdBy.id != userId) {
                throw NotMatchCreatorException()
            }

            val response = mapMatchToResponse(match)

            logger.info("\n\t[INFO] [match_service][get_match] Retrieved match {}", matchId)
            return response
        } catch(e: MatchNotFoundException) {
            logger.warn("\n\t[WARN] [match_service][get_match] Match {} not found", matchId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][get_match] Error retrieving match {}: {}", matchId, e.message)
            throw e
        }
    }

    @Transactional
    fun listMyMatches(userId: UUID, page: Int, size: Int, gameId: UUID?, fromDate: LocalDate?, toDate: LocalDate?, sort: String?): PageDTO<MatchDTO> {
        logger.debug("\n\t[DEBUG] [match_service][list_my_matches] Listing matches\n\tuserId={}\n\tpage={}\n\tsize={}\n\tgameId={}\n\tfromDate={}\n\ttoDate={}", userId, page, size, gameId, fromDate, toDate)

        try {
            val pageSafe = if(page < 0) 0 else page
            val sizeSafe = size.coerceIn(1, 100)
            val sortObj = resolveSort(sort, setOf("playedAt", "createdAt"), "playedAt")
            val pageable = PageRequest.of(pageSafe, sizeSafe, sortObj)

            val spec = MatchSpecification.withFilters(userId, gameId, fromDate, toDate)
            val result = matchRepository.findAll(spec, pageable)

            logger.info("\n\t[INFO] [match_service][list_my_matches] Retrieved {} matches for user {}", result.numberOfElements, userId)
            return result.toPageDTO { mapMatchToResponse(it) }
        } catch(e: InvalidSortException) {
            logger.warn("\n\t[WARN] [match_service][list_my_matches] Invalid sort field: {}", sort)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][list_my_matches] Error listing matches for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun getMatchCalendar(userId: UUID, year: Int, month: Int): List<MatchDayCountResponse> {
        logger.debug("\n\t[DEBUG] [match_service][get_match_calendar] Retrieving calendar\n\tuserId={}\n\tyear={}\n\tmonth={}", userId, year, month)

        try {
            val from = LocalDate.of(year, month, 1)
            val to = from.withDayOfMonth(from.lengthOfMonth())

            val counts = matchRepository.countMatchesByDay(userId, from, to)
                .map { MatchDayCountResponse(date = it.playedAt, count = it.matchCount) }

            logger.info("\n\t[INFO] [match_service][get_match_calendar] Retrieved {} days with matches for user {}", counts.size, userId)
            return counts
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][get_match_calendar] Error retrieving calendar for user {}: {}", userId, e.message)
            throw e
        }
    }

    private fun validateRequest(request: CreateMatchRequest) {
        if(request.isTeamBased) {
            if(request.teams.isNullOrEmpty()) {
                throw InvalidMatchTeamsException("At least one team is required when isTeamBased is true")
            }

            if(!request.players.isNullOrEmpty()) {
                throw InvalidMatchPlayersException("Individual players must not be provided when isTeamBased is true")
            }

            request.teams.forEach { team ->
                team.players.forEach { validateIdentity(it.userId, it.guestName) }
            }
        } else {
            if(request.players.isNullOrEmpty()) {
                throw InvalidMatchPlayersException("At least one player is required when isTeamBased is false")
            }

            if(!request.teams.isNullOrEmpty()) {
                throw InvalidMatchTeamsException("Teams must not be provided when isTeamBased is false")
            }

            request.players.forEach { validateIdentity(it.userId, it.guestName) }
        }
    }

    private fun validateIdentity(userId: UUID?, guestName: String?) {
        val hasUser = userId != null
        val hasGuest = !guestName.isNullOrBlank()

        if(hasUser == hasGuest) {
            throw InvalidMatchPlayerIdentityException()
        }
    }

    private fun buildMatchPlayersAndTeams(match: Match, request: CreateMatchRequest): MatchDTO {
        return if(request.isTeamBased) {
            val teamResponses = request.teams!!.map { teamRequest ->
                val team = matchTeamRepository.save(
                    MatchTeam(
                        match = match,
                        name = teamRequest.name,
                        color = teamRequest.color,
                        score = teamRequest.score,
                        isWinner = teamRequest.isWinner,
                        startingPosition = teamRequest.startingPosition,
                    )
                )

                val playerRefs = teamRequest.players.map { playerRequest ->
                    MatchPlayerRefDTO.from(saveTeamPlayer(match, team, playerRequest))
                }

                MatchTeamDTO.from(team, playerRefs)
            }

            MatchDTO.from(match, teamResponses, null)
        } else {
            val playerResponses = request.players!!.map { playerRequest ->
                MatchPlayerDTO.from(saveIndividualPlayer(match, playerRequest))
            }

            MatchDTO.from(match, null, playerResponses)
        }
    }

    private fun saveTeamPlayer(match: Match, team: MatchTeam, request: MatchPlayerIdentityRequest): MatchPlayer {
        val user = request.userId?.let {
            userRepository.findById(it).orElseThrow { UserNotFoundByIdentifierException(it.toString()) }
        }

        return matchPlayerRepository.save(
            MatchPlayer(
                match = match,
                team = team,
                user = user,
                guestName = if(user == null) request.guestName else null,
            )
        )
    }

    private fun saveIndividualPlayer(match: Match, request: MatchIndividualPlayerRequest): MatchPlayer {
        val user = request.userId?.let {
            userRepository.findById(it).orElseThrow { UserNotFoundByIdentifierException(it.toString()) }
        }

        return matchPlayerRepository.save(
            MatchPlayer(
                match = match,
                team = null,
                user = user,
                guestName = if(user == null) request.guestName else null,
                color = request.color,
                score = request.score,
                isWinner = request.isWinner,
                startingPosition = request.startingPosition,
            )
        )
    }

    private fun mapMatchToResponse(match: Match): MatchDTO {
        return if(match.isTeamBased) {
            val teams = matchTeamRepository.findAllByMatchId(match.id!!)
            val allPlayers = matchPlayerRepository.findAllByMatchId(match.id!!)

            val teamResponses = teams.map { team ->
                val teamPlayers = allPlayers.filter { it.team?.id == team.id }.map { MatchPlayerRefDTO.from(it) }
                MatchTeamDTO.from(team, teamPlayers)
            }

            MatchDTO.from(match, teamResponses, null)
        } else {
            val players = matchPlayerRepository.findAllByMatchId(match.id!!).map { MatchPlayerDTO.from(it) }
            MatchDTO.from(match, null, players)
        }
    }
}