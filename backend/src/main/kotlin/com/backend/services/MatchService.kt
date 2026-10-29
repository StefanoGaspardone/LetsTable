package com.backend.services

import com.backend.exceptions.*
import com.backend.models.dtos.CreateMatchRequest
import com.backend.models.dtos.GameDTO
import com.backend.models.dtos.GameMatchStatsDTO
import com.backend.models.dtos.MatchDTO
import com.backend.models.dtos.MatchDayCountDTO
import com.backend.models.dtos.MatchPlayerDTO
import com.backend.models.dtos.MatchPlayerRefDTO
import com.backend.models.dtos.MatchPlayersPayload
import com.backend.models.dtos.MatchTeamDTO
import com.backend.models.dtos.MatchWinStatsDTO
import com.backend.models.dtos.PageDTO
import com.backend.models.dtos.UpdateMatchRequest
import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.MatchTeam
import com.backend.models.entities.User
import com.backend.models.mappers.toPageDTO
import com.backend.models.specifications.MatchSpecification
import com.backend.repositories.GameRepository
import com.backend.repositories.MatchPlayerRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.MatchTeamRepository
import com.backend.repositories.UserRepository
import com.backend.utils.resolveSort
import com.backend.utils.safePageRequest
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

@Service
class MatchService(
    private val matchRepository: MatchRepository,
    private val matchTeamRepository: MatchTeamRepository,
    private val matchPlayerRepository: MatchPlayerRepository,
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository,
    private val userAchievementService: UserAchievementService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val MATCH_SORT_FIELDS = setOf("playedAt", "createdAt", "durationMinutes")
        private const val DEFAULT_MATCH_SORT_FIELD = "playedAt"
        private val MATCH_NULLS_LAST_FIELDS = setOf("durationMinutes")
        private val MATCH_TIE_BREAKER: Sort = Sort.by(Sort.Order.desc("playedAt"), Sort.Order.desc("createdAt"))
        private val GAME_STATS_SORT_FIELDS = setOf("matchCount", "totalMinutes", "avgMinutes", "winRate", "lastPlayedAt", "gameName")
        private const val DEFAULT_GAME_STATS_SORT_FIELD = "matchCount"
        private val GAME_STATS_TIE_BREAKER: Sort = Sort.by(Sort.Order.asc("gameName"))
    }

    @Transactional
    fun createMatch(userId: UUID, request: CreateMatchRequest): MatchDTO {
        logger.debug("\n\t[DEBUG] [match_service][create_match] User {} creating match for game {}", userId, request.gameId)

        try {
            validateRequest(request)

            val game = findGame(request.gameId)
            val createdBy = userRepository.findById(userId)
                .orElseThrow { UserNotFoundByIdentifierException(userId.toString()) }

            val match = matchRepository.save(
                Match(
                    game = game,
                    createdBy = createdBy,
                    isTeamBased = request.isTeamBased,
                    playedAt = combineDateWithCurrentTime(request.playedAt),
                    place = request.place,
                    notes = request.notes,
                    durationMinutes = request.durationMinutes,
                    expansionsUsed = resolveExpansions(request.expansionIds, game).toMutableSet(),
                )
            )

            val (teams, players) = savePlayersAndTeams(match, request)

            if(match.durationMinutes != null) {
                userAchievementService.evaluateAfterCommit(participantUserIds(request))
            }

            logger.info("\n\t[INFO] [match_service][create_match] Match {} created by user {}", match.id, userId)
            return toDTO(match, teams, players)
        } catch(e: InvalidMatchTeamsException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Invalid teams payload from user {}: {}", userId, e.message)
            throw e
        } catch(e: InvalidMatchPlayersException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Invalid players payload from user {}: {}", userId, e.message)
            throw e
        } catch(e: InvalidMatchPlayerIdentityException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Invalid player identity from user {}", userId)
            throw e
        } catch(e: InvalidExpansionForGameException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Invalid expansion for game {}: {}", request.gameId, e.message)
            throw e
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Game not found: {}", e.message)
            throw e
        } catch(e: UserNotFoundByIdentifierException) {
            logger.warn("\n\t[WARN] [match_service][create_match] Referenced user not found: {}", e.message)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][create_match] Error creating match for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun updateMatch(userId: UUID, matchId: UUID, request: UpdateMatchRequest): MatchDTO {
        logger.debug("\n\t[DEBUG] [match_service][update_match] User {} updating match {}", userId, matchId)

        try {
            val match = findOwnedMatch(userId, matchId)

            validateRequest(request)

            val game = findGame(request.gameId)

            match.game = game
            match.isTeamBased = request.isTeamBased
            match.place = request.place
            match.notes = request.notes
            match.expansionsUsed = resolveExpansions(request.expansionIds, game).toMutableSet()

            if(match.playedAt.atZone(ZoneOffset.UTC).toLocalDate() != request.playedAt) {
                match.playedAt = combineDateWithCurrentTime(request.playedAt)
            }

            if(request.durationMinutes != null) {
                match.durationMinutes = request.durationMinutes
            } else if(match.durationMinutes == null) {
                match.durationMinutes = Duration.between(match.createdAt, Instant.now()).toMinutes().toInt().coerceAtLeast(1)
            }

            matchPlayerRepository.deleteAllByMatchId(matchId)
            matchTeamRepository.deleteAllByMatchId(matchId)

            val (teams, players) = savePlayersAndTeams(match, request)

            userAchievementService.evaluateAfterCommit(participantUserIds(request))

            logger.info("\n\t[INFO] [match_service][update_match] Match {} updated by user {}", matchId, userId)
            return toDTO(match, teams, players)
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
        } catch(e: InvalidExpansionForGameException) {
            logger.warn("\n\t[WARN] [match_service][update_match] Invalid expansion for game {}: {}", request.gameId, e.message)
            throw e
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [match_service][update_match] Game not found: {}", e.message)
            throw e
        } catch(e: UserNotFoundByIdentifierException) {
            logger.warn("\n\t[WARN] [match_service][update_match] Referenced user not found: {}", e.message)
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
            matchRepository.delete(findOwnedMatch(userId, matchId))

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

    @Transactional(readOnly = true)
    fun getMatch(matchId: UUID): MatchDTO {
        logger.debug("\n\t[DEBUG] [match_service][get_match] Retrieving match {}", matchId)

        try {
            val match = matchRepository.findById(matchId)
                .orElseThrow { MatchNotFoundException(matchId) }

            logger.info("\n\t[INFO] [match_service][get_match] Retrieved match {}", matchId)
            return mapMatchesToResponse(listOf(match)).first()
        } catch(e: MatchNotFoundException) {
            logger.warn("\n\t[WARN] [match_service][get_match] Match {} not found", matchId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][get_match] Error retrieving match {}: {}", matchId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun listMyMatches(userId: UUID, page: Int, size: Int, gameId: UUID?, fromDate: LocalDate?, toDate: LocalDate?, sort: String?, onlyCompleted: Boolean = false): PageDTO<MatchDTO> {
        logger.debug("\n\t[DEBUG] [match_service][list_my_matches] Listing matches\n\tuserId={}\n\tpage={}\n\tsize={}\n\tgameId={}\n\tfromDate={}\n\ttoDate={}\n\tsort={}\n\tonlyCompleted={}", userId, page, size, gameId, fromDate, toDate, sort, onlyCompleted)

        try {
            val sortObj = resolveSort(
                sort = sort,
                allowedFields = MATCH_SORT_FIELDS,
                defaultField = DEFAULT_MATCH_SORT_FIELD,
                nullsLastFields = MATCH_NULLS_LAST_FIELDS,
                tieBreaker = MATCH_TIE_BREAKER,
            )

            val result = matchRepository.findAll(MatchSpecification.withFilters(userId, gameId, fromDate, toDate, onlyCompleted), safePageRequest(page, size, sortObj))
            val dtoById = mapMatchesToResponse(result.content).associateBy { it.id }

            logger.info("\n\t[INFO] [match_service][list_my_matches] Retrieved {} matches for user {}", result.numberOfElements, userId)
            return result.toPageDTO { dtoById.getValue(it.id!!) }
        } catch(e: InvalidSortException) {
            logger.warn("\n\t[WARN] [match_service][list_my_matches] Invalid sort field: {}", sort)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][list_my_matches] Error listing matches for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getMatchCalendar(userId: UUID, year: Int, month: Int): List<MatchDayCountDTO> {
        logger.debug("\n\t[DEBUG] [match_service][get_match_calendar] Retrieving calendar\n\tuserId={}\n\tyear={}\n\tmonth={}", userId, year, month)

        try {
            val yearMonth = YearMonth.of(year, month)
            val from = yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            val to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()

            val counts = matchRepository.countMatchesByDay(userId, from, to)
                .map { MatchDayCountDTO(date = it.playedAt, count = it.matchCount) }

            logger.info("\n\t[INFO] [match_service][get_match_calendar] Retrieved {} days with matches for user {}", counts.size, userId)
            return counts
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][get_match_calendar] Error retrieving calendar for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getRecentGames(userId: UUID, limit: Int = 10): List<GameDTO> {
        logger.debug("\n\t[DEBUG] [match_service][get_recent_games] Retrieving recent games for user {}", userId)

        try {
            val recentMatches = matchRepository.findRecentForUser(userId, PageRequest.of(0, limit))
            val response = recentMatches.map { it.game }.distinctBy { it.id }.map { GameDTO.from(it) }

            logger.info("\n\t[INFO] [match_service][get_recent_games] Found {} distinct games in last {} matches for user {}", response.size, recentMatches.size, userId)
            return response
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][get_recent_games] Error retrieving recent games for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getWinStats(userId: UUID): MatchWinStatsDTO {
        logger.debug("\n\t[DEBUG] [match_service][get_win_stats] Retrieving win stats for user {}", userId)

        try {
            val response = MatchWinStatsDTO(
                totalMatches = matchRepository.countCompletedMatchesForUser(userId),
                totalWins = matchRepository.countWonMatchesForUser(userId),
            )

            logger.info("\n\t[INFO] [match_service][get_win_stats] User {} has {} wins out of {} matches", userId, response.totalWins, response.totalMatches)
            return response
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][get_win_stats] Error retrieving win stats for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getRecentMatchesForUser(userId: UUID, limit: Int = 10): List<MatchDTO> {
        logger.debug("\n\t[DEBUG] [match_service][get_recent_matches_for_user] Retrieving recent matches for user {}", userId)

        try {
            val response = mapMatchesToResponse(matchRepository.findRecentCompletedForUser(userId, PageRequest.of(0, limit)))

            logger.info("\n\t[INFO] [match_service][get_recent_matches_for_user] Retrieved {} matches for user {}", response.size, userId)
            return response
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][get_recent_matches_for_user] Error retrieving matches for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun listGameStats(userId: UUID, page: Int, size: Int, sort: String?): PageDTO<GameMatchStatsDTO> {
        logger.debug("\n\t[DEBUG] [match_service][list_game_stats] Listing game stats\n\tuserId={}\n\tpage={}\n\tsize={}\n\tsort={}", userId, page, size, sort)

        try {
            val sortObj = resolveSort(
                sort = sort,
                allowedFields = GAME_STATS_SORT_FIELDS,
                defaultField = DEFAULT_GAME_STATS_SORT_FIELD,
                tieBreaker = GAME_STATS_TIE_BREAKER,
            )

            val result = matchRepository.findGameStatsForUser(userId, safePageRequest(page, size, sortObj))

            logger.info("\n\t[INFO] [match_service][list_game_stats] Retrieved {} game stats for user {}", result.numberOfElements, userId)
            return result.toPageDTO { GameMatchStatsDTO.from(it) }
        } catch(e: InvalidSortException) {
            logger.warn("\n\t[WARN] [match_service][list_game_stats] Invalid sort field: {}", sort)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_service][list_game_stats] Error listing game stats for user {}: {}", userId, e.message)
            throw e
        }
    }

    private fun findOwnedMatch(userId: UUID, matchId: UUID): Match {
        val match = matchRepository.findById(matchId)
            .orElseThrow { MatchNotFoundException(matchId) }

        if(match.createdBy.id != userId) {
            throw NotMatchCreatorException()
        }

        return match
    }

    private fun findGame(gameId: UUID): Game =
        gameRepository.findById(gameId)
            .orElseThrow { GameNotFoundException(gameId) }

    private fun validateRequest(request: MatchPlayersPayload) {
        val teams = request.teams
        val players = request.players

        if(request.isTeamBased) {
            if(teams.isNullOrEmpty()) {
                throw InvalidMatchTeamsException("At least one team is required when isTeamBased is true")
            }

            if(!players.isNullOrEmpty()) {
                throw InvalidMatchPlayersException("Individual players must not be provided when isTeamBased is true")
            }

            teams.forEach { team -> team.players.forEach { validateIdentity(it.userId, it.guestName) } }
        } else {
            if(players.isNullOrEmpty()) {
                throw InvalidMatchPlayersException("At least one player is required when isTeamBased is false")
            }

            if(!teams.isNullOrEmpty()) {
                throw InvalidMatchTeamsException("Teams must not be provided when isTeamBased is false")
            }

            players.forEach { validateIdentity(it.userId, it.guestName) }
        }

        val userIds = participantUserIdList(request)

        if(userIds.size != userIds.toSet().size) {
            throw InvalidMatchPlayersException("Each registered user can appear only once in a match")
        }
    }

    private fun validateIdentity(userId: UUID?, guestName: String?) {
        if((userId != null) == !guestName.isNullOrBlank()) {
            throw InvalidMatchPlayerIdentityException()
        }
    }

    private fun savePlayersAndTeams(match: Match, request: MatchPlayersPayload): Pair<List<MatchTeam>, List<MatchPlayer>> {
        val usersById = loadUsers(participantUserIds(request))

        if(!request.isTeamBased) {
            val players = matchPlayerRepository.saveAll(
                request.players!!.map { player ->
                    MatchPlayer(
                        match = match,
                        team = null,
                        user = player.userId?.let { usersById.getValue(it) },
                        guestName = if(player.userId == null) player.guestName else null,
                        color = player.color,
                        score = player.score,
                        isWinner = player.isWinner,
                        isStartingFirst = player.isStartingFirst,
                    )
                }
            )

            return emptyList<MatchTeam>() to players
        }

        val teamRequests = request.teams!!
        val teams = matchTeamRepository.saveAll(
            teamRequests.map { team ->
                MatchTeam(
                    match = match,
                    name = team.name,
                    color = team.color,
                    score = team.score,
                    isWinner = team.isWinner,
                    isStartingFirst = team.isStartingFirst,
                )
            }
        )

        val players = matchPlayerRepository.saveAll(
            teams.zip(teamRequests).flatMap { (team, teamRequest) ->
                teamRequest.players.map { player ->
                    MatchPlayer(
                        match = match,
                        team = team,
                        user = player.userId?.let { usersById.getValue(it) },
                        guestName = if(player.userId == null) player.guestName else null,
                    )
                }
            }
        )

        return teams to players
    }

    private fun loadUsers(userIds: Set<UUID>): Map<UUID, User> {
        if(userIds.isEmpty()) {
            return emptyMap()
        }

        val usersById = userRepository.findAllById(userIds).associateBy { it.id!! }

        userIds.firstOrNull { it !in usersById }?.let { throw UserNotFoundByIdentifierException(it.toString()) }

        return usersById
    }

    private fun mapMatchesToResponse(matches: List<Match>): List<MatchDTO> {
        if(matches.isEmpty()) {
            return emptyList()
        }

        val matchIds = matches.map { it.id!! }
        val playersByMatchId = matchPlayerRepository.findAllByMatchIdIn(matchIds).groupBy { it.match.id }
        val teamsByMatchId = matchTeamRepository.findAllByMatchIdIn(matchIds).groupBy { it.match.id }

        return matches.map { toDTO(it, teamsByMatchId[it.id].orEmpty(), playersByMatchId[it.id].orEmpty()) }
    }

    private fun toDTO(match: Match, teams: List<MatchTeam>, players: List<MatchPlayer>): MatchDTO {
        if(!match.isTeamBased) {
            return MatchDTO.from(match, null, players.map { MatchPlayerDTO.from(it) })
        }

        val playersByTeamId = players.groupBy { it.team?.id }
        val teamResponses = teams.map { team ->
            MatchTeamDTO.from(team, playersByTeamId[team.id].orEmpty().map { MatchPlayerRefDTO.from(it) })
        }

        return MatchDTO.from(match, teamResponses, null)
    }

    private fun resolveExpansions(expansionIds: List<UUID>, baseGame: Game): List<Game> {
        if(expansionIds.isEmpty()) {
            return emptyList()
        }

        val expansions = gameRepository.findAllById(expansionIds)
        val foundIds = expansions.mapNotNull { it.id }.toSet()

        expansionIds.firstOrNull { it !in foundIds }?.let { throw GameNotFoundException(it) }

        val baseGameBggIds = baseGame.expansionRefs.map { it.bggId }.toSet()

        expansions.firstOrNull { it.bggId !in baseGameBggIds }?.let { throw InvalidExpansionForGameException(it.id!!) }

        return expansions
    }

    private fun combineDateWithCurrentTime(date: LocalDate): Instant =
        LocalDateTime.of(date, Instant.now().atZone(ZoneOffset.UTC).toLocalTime()).atZone(ZoneOffset.UTC).toInstant()

    private fun participantUserIdList(request: MatchPlayersPayload): List<UUID> =
        request.teams.orEmpty().flatMap { team -> team.players.mapNotNull { it.userId } } +
                request.players.orEmpty().mapNotNull { it.userId }

    private fun participantUserIds(request: MatchPlayersPayload): Set<UUID> =
        participantUserIdList(request).toSet()
}