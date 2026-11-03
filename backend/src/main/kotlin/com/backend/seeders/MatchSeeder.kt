package com.backend.seeders

import com.backend.models.entities.*
import com.backend.repositories.MatchPlayerRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.MatchTeamRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class MatchSeeder(
    private val matchRepository: MatchRepository,
    private val matchTeamRepository: MatchTeamRepository,
    private val matchPlayerRepository: MatchPlayerRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val COLOR_1 = "#C45135"
        private const val COLOR_2 = "#3B6E91"
        private const val COLOR_3 = "#5C8A4F"
        private const val COLOR_4 = "#B08968"
        private const val COLOR_5 = "#7C5CBF"
        private const val COLOR_6 = "#D4A017"
    }

    private data class SeedPlayer(
        val user: User? = null,
        val guestName: String? = null,
        val color: String? = null,
        val score: Int? = null,
        val isWinner: Boolean = false,
        val isStartingFirst: Boolean = false,
    )

    private data class SeedTeam(
        val name: String,
        val color: String,
        val score: Int? = null,
        val isWinner: Boolean = false,
        val isStartingFirst: Boolean = false,
        val members: List<SeedPlayer>,
    )

    private data class SeedInfo(
        val game: Game,
        val createdBy: User,
        val daysAgo: Long,
        val hoursAgo: Long = 0,
        val durationMinutes: Int?,
        val place: String? = null,
        val notes: String? = null,
    )

    fun seed(users: List<User>, games: List<Game>) {
        logger.debug("\n\t[DEBUG] [match_seeder][seed] Seeding demo matches")

        try {
            val (marco, anna, luca, elena) = users

            val gamesByBggId = games.associateBy { it.bggId }
            val arkNova = gamesByBggId[342942L] ?: games[0]
            val seti = gamesByBggId[397598L] ?: games[1]
            val twilight = gamesByBggId[233078L] ?: games[3]
            val coopGame = games.getOrElse(4) { games[0] }
            val lowScoreGame = games.getOrElse(5) { games[2] }
            val quickGame = games.getOrElse(6) { games[2] }

            val matches = listOf(
                seedIndividual(
                    SeedInfo(arkNova, anna, daysAgo = 0, hoursAgo = 3, durationMinutes = 120, place = "Casa di Anna", notes = "Rimonta all'ultimo turno, decisa per due punti."),
                    listOf(
                        SeedPlayer(marco, color = COLOR_1, score = 160, isStartingFirst = true),
                        SeedPlayer(anna, color = COLOR_2, score = 162, isWinner = true),
                        SeedPlayer(luca, color = COLOR_3, score = 141),
                        SeedPlayer(elena, color = COLOR_4, score = 133),
                    ),
                ),
                seedIndividual(
                    SeedInfo(quickGame, marco, daysAgo = 0, hoursAgo = 6, durationMinutes = 25, place = "Bar sotto casa"),
                    listOf(
                        SeedPlayer(marco, color = COLOR_1, score = 1, isWinner = true, isStartingFirst = true),
                        SeedPlayer(anna, color = COLOR_2, score = 0),
                    ),
                ),
                seedIndividual(
                    SeedInfo(seti, anna, daysAgo = 0, durationMinutes = null),
                    listOf(
                        SeedPlayer(anna, color = COLOR_1, score = 0, isStartingFirst = true),
                        SeedPlayer(luca, color = COLOR_2, score = 0),
                        SeedPlayer(elena, color = COLOR_3, score = 0),
                    ),
                ),
                seedTeams(
                    SeedInfo(twilight, marco, daysAgo = 0, hoursAgo = 1, durationMinutes = null, place = "Ludoteca centrale"),
                    listOf(
                        SeedTeam("Impero", COLOR_1, score = 0, isStartingFirst = true, members = listOf(SeedPlayer(marco), SeedPlayer(elena))),
                        SeedTeam("Ribelli", COLOR_2, score = 0, members = listOf(SeedPlayer(luca), SeedPlayer(guestName = "Paolo"))),
                    ),
                ),
                seedTeams(
                    SeedInfo(coopGame, luca, daysAgo = 1, hoursAgo = 4, durationMinutes = 75, place = "Casa di Luca", notes = "Partita cooperativa contro il gioco, vittoria all'ultimo turno."),
                    listOf(
                        SeedTeam("Squadra", COLOR_1, isWinner = true, isStartingFirst = true, members = listOf(SeedPlayer(luca), SeedPlayer(anna), SeedPlayer(guestName = "Riccardo"))),
                    ),
                ),
                seedIndividual(
                    SeedInfo(arkNova, marco, daysAgo = 2, hoursAgo = 3, durationMinutes = 95, place = "Casa di Marco", notes = "Prima partita ad Ark Nova, molto tirata fino alla fine."),
                    listOf(
                        SeedPlayer(marco, color = COLOR_1, score = 145, isWinner = true, isStartingFirst = true),
                        SeedPlayer(anna, color = COLOR_2, score = 132),
                        SeedPlayer(guestName = "Giulia", color = COLOR_3, score = 98),
                    ),
                ),
                seedIndividual(
                    SeedInfo(arkNova, elena, daysAgo = 3, durationMinutes = 60, place = "Casa di Elena", notes = "Nessuno ha tenuto i punti, conta solo chi ha vinto."),
                    listOf(
                        SeedPlayer(elena, color = COLOR_1, isWinner = true),
                        SeedPlayer(marco, color = COLOR_2, isStartingFirst = true),
                        SeedPlayer(luca, color = COLOR_3),
                    ),
                ),
                seedIndividual(
                    SeedInfo(lowScoreGame, luca, daysAgo = 4, hoursAgo = 2, durationMinutes = 30, notes = "Vince chi fa meno punti."),
                    listOf(
                        SeedPlayer(luca, color = COLOR_1, score = 12, isWinner = true),
                        SeedPlayer(marco, color = COLOR_2, score = 18, isStartingFirst = true),
                        SeedPlayer(anna, color = COLOR_3, score = 25),
                    ),
                ),
                seedIndividual(
                    SeedInfo(arkNova, luca, daysAgo = 5, hoursAgo = 1, durationMinutes = 80, place = "Casa di Luca", notes = "Pareggio perfetto al primo posto."),
                    listOf(
                        SeedPlayer(marco, color = COLOR_1, score = 150, isWinner = true),
                        SeedPlayer(luca, color = COLOR_2, score = 150, isWinner = true, isStartingFirst = true),
                        SeedPlayer(elena, color = COLOR_3, score = 120),
                    ),
                ),
                seedTeams(
                    SeedInfo(twilight, luca, daysAgo = 5, hoursAgo = 6, durationMinutes = 420, place = "Ludoteca centrale", notes = "Partita epica durata quasi tutto il pomeriggio."),
                    listOf(
                        SeedTeam("Impero", COLOR_1, score = 12, isWinner = true, isStartingFirst = true, members = listOf(SeedPlayer(marco), SeedPlayer(luca))),
                        SeedTeam("Ribelli", COLOR_2, score = 8, members = listOf(SeedPlayer(anna), SeedPlayer(guestName = "Paolo"))),
                        SeedTeam("Mercanti", COLOR_3, score = 6, members = listOf(SeedPlayer(elena))),
                        SeedTeam("Nomadi", COLOR_4, score = 9, members = listOf(SeedPlayer(guestName = "Francesca"), SeedPlayer(guestName = "Davide"))),
                        SeedTeam("Federazione", COLOR_5, score = 5, members = listOf(SeedPlayer(guestName = "Sara"))),
                    ),
                ),
                seedTeams(
                    SeedInfo(coopGame, elena, daysAgo = 7, hoursAgo = 2, durationMinutes = 130, place = "Ludoteca centrale", notes = "Due squadre hanno raggiunto l'obiettivo insieme, una è stata eliminata."),
                    listOf(
                        SeedTeam("Squadra Rossa", COLOR_1, isWinner = true, isStartingFirst = true, members = listOf(SeedPlayer(elena), SeedPlayer(guestName = "Noemi"))),
                        SeedTeam("Squadra Blu", COLOR_2, isWinner = true, members = listOf(SeedPlayer(marco), SeedPlayer(anna))),
                        SeedTeam("Squadra Verde", COLOR_3, members = listOf(SeedPlayer(luca))),
                    ),
                ),
                seedIndividual(
                    SeedInfo(seti, marco, daysAgo = 9, durationMinutes = 150, place = "Casa di Marco", notes = "Tavolo pieno, sei giocatori."),
                    listOf(
                        SeedPlayer(marco, color = COLOR_1, score = 84, isStartingFirst = true),
                        SeedPlayer(anna, color = COLOR_2, score = 91),
                        SeedPlayer(luca, color = COLOR_3, score = 77),
                        SeedPlayer(elena, color = COLOR_4, score = 98, isWinner = true),
                        SeedPlayer(guestName = "Giulia", color = COLOR_5, score = 69),
                        SeedPlayer(guestName = "Paolo", color = COLOR_6, score = 72),
                    ),
                ),
                seedTeams(
                    SeedInfo(arkNova, anna, daysAgo = 10, durationMinutes = 110, place = "Casa di Anna", notes = "Finale testa a testa, pareggio secco al primo posto."),
                    listOf(
                        SeedTeam("Falchi", COLOR_1, score = 87, isWinner = true, isStartingFirst = true, members = listOf(SeedPlayer(anna), SeedPlayer(guestName = "Chiara"))),
                        SeedTeam("Lupi", COLOR_2, score = 87, isWinner = true, members = listOf(SeedPlayer(marco), SeedPlayer(luca))),
                        SeedTeam("Orsi", COLOR_3, score = 64, members = listOf(SeedPlayer(elena), SeedPlayer(guestName = "Tommaso"))),
                    ),
                ),
                seedTeams(
                    SeedInfo(seti, elena, daysAgo = 12, hoursAgo = 5, durationMinutes = 45, place = "Casa di Elena"),
                    listOf(
                        SeedTeam("Esploratori", COLOR_1, score = 210, isWinner = true, isStartingFirst = true, members = listOf(SeedPlayer(elena), SeedPlayer(marco))),
                        SeedTeam("Astronomi", COLOR_2, score = 176, members = listOf(SeedPlayer(anna), SeedPlayer(luca))),
                    ),
                ),
                seedIndividual(
                    SeedInfo(arkNova, marco, daysAgo = 35, durationMinutes = 100, place = "Casa di Marco", notes = "Vinta per un punto."),
                    listOf(
                        SeedPlayer(marco, color = COLOR_1, score = 145, isWinner = true),
                        SeedPlayer(anna, color = COLOR_2, score = 144, isStartingFirst = true),
                    ),
                ),
                seedIndividual(
                    SeedInfo(quickGame, anna, daysAgo = 40, hoursAgo = 2, durationMinutes = 10, place = "In treno"),
                    listOf(
                        SeedPlayer(anna, color = COLOR_1, isWinner = true, isStartingFirst = true),
                        SeedPlayer(luca, color = COLOR_2),
                    ),
                ),
            )

            logger.info("\n\t[INFO] [match_seeder][seed] {} demo matches seeded", matches.size)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_seeder][seed] Error seeding matches: {}", e.message)
            throw e
        }
    }

    private fun seedIndividual(info: SeedInfo, players: List<SeedPlayer>): Match {
        val winnerScore = players.filter { it.isWinner }.mapNotNull { it.score }.maxOrNull()
        val match = saveMatch(info, isTeamBased = false, winnerScore = winnerScore)

        matchPlayerRepository.saveAll(
            players.map {
                MatchPlayer(
                    match = match,
                    user = it.user,
                    guestName = it.guestName,
                    color = it.color,
                    score = it.score,
                    isWinner = it.isWinner,
                    isStartingFirst = it.isStartingFirst,
                )
            }
        )

        return match
    }

    private fun seedTeams(info: SeedInfo, teams: List<SeedTeam>): Match {
        val winnerScore = teams.filter { it.isWinner }.mapNotNull { it.score }.maxOrNull()
        val match = saveMatch(info, isTeamBased = true, winnerScore = winnerScore)

        teams.forEach { seedTeam ->
            val team = matchTeamRepository.save(
                MatchTeam(
                    match = match,
                    name = seedTeam.name,
                    color = seedTeam.color,
                    score = seedTeam.score,
                    isWinner = seedTeam.isWinner,
                    isStartingFirst = seedTeam.isStartingFirst,
                )
            )

            matchPlayerRepository.saveAll(
                seedTeam.members.map { MatchPlayer(match = match, team = team, user = it.user, guestName = it.guestName) }
            )
        }

        return match
    }

    private fun saveMatch(info: SeedInfo, isTeamBased: Boolean, winnerScore: Int?): Match =
        matchRepository.save(
            Match(
                game = info.game,
                createdBy = info.createdBy,
                isTeamBased = isTeamBased,
                playedAt = Instant.now().minus(info.daysAgo, ChronoUnit.DAYS).minus(info.hoursAgo, ChronoUnit.HOURS),
                place = info.place,
                notes = info.notes,
                durationMinutes = info.durationMinutes,
                winnerScore = if(info.durationMinutes != null) winnerScore else null,
            )
        )
}