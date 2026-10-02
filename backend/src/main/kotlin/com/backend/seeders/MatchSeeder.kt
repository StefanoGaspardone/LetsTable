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

    private val COLOR_1 = "#C45135"
    private val COLOR_2 = "#3B6E91"
    private val COLOR_3 = "#5C8A4F"

    fun seed(users: List<User>, games: List<Game>) {
        logger.debug("\n\t[DEBUG] [match_seeder][seed] Seeding demo matches")

        try {
            val (marco, anna, luca, elena) = users

            val gamesByBggId = games.associateBy { it.bggId }
            val arkNova = gamesByBggId[342942L] ?: games[0]
            val seti = gamesByBggId[397598L] ?: games[1]
            val twilight = gamesByBggId[233078L] ?: games[3]
            val coopGame = games.getOrElse(4) { games[0] }

            val individualMatch = matchRepository.save(
                Match(
                    game = arkNova,
                    createdBy = marco,
                    isTeamBased = false,
                    playedAt = Instant.now().minus(2, ChronoUnit.DAYS).minus(3, ChronoUnit.HOURS),
                    place = "Casa di Marco",
                    notes = "Prima partita ad Ark Nova, molto tirata fino alla fine.",
                    durationMinutes = 95,
                )
            )
            listOf(
                MatchPlayer(match = individualMatch, user = marco, color = COLOR_1, score = 145, isWinner = true, isStartingFirst = true),
                MatchPlayer(match = individualMatch, user = anna, color = COLOR_2, score = 132, isWinner = false),
                MatchPlayer(match = individualMatch, user = null, guestName = "Giulia", color = COLOR_3, score = 98, isWinner = false),
            ).forEach { matchPlayerRepository.save(it) }

            val teamMatch = matchRepository.save(
                Match(
                    game = twilight,
                    createdBy = luca,
                    isTeamBased = true,
                    playedAt = Instant.now().minus(5, ChronoUnit.DAYS).minus(6, ChronoUnit.HOURS),
                    place = "Ludoteca centrale",
                    notes = "Partita epica durata quasi tutto il pomeriggio.",
                    durationMinutes = 420,
                )
            )

            val teamA = matchTeamRepository.save(
                MatchTeam(match = teamMatch, name = "Impero", color = COLOR_1, score = 12, isWinner = true, isStartingFirst = true)
            )
            val teamB = matchTeamRepository.save(
                MatchTeam(match = teamMatch, name = "Ribelli", color = COLOR_2, score = 8, isWinner = false)
            )
            val teamC = matchTeamRepository.save(
                MatchTeam(match = teamMatch, name = "Mercanti", color = COLOR_3, score = 6, isWinner = false)
            )
            val teamD = matchTeamRepository.save(
                MatchTeam(match = teamMatch, name = "Nomadi", color = "#B08968", score = 9, isWinner = false)
            )
            val teamE = matchTeamRepository.save(
                MatchTeam(match = teamMatch, name = "Federazione", color = "#7C5CBF", score = 5, isWinner = false)
            )

            listOf(
                MatchPlayer(match = teamMatch, team = teamA, user = marco),
                MatchPlayer(match = teamMatch, team = teamA, user = luca),
                MatchPlayer(match = teamMatch, team = teamB, user = anna),
                MatchPlayer(match = teamMatch, team = teamB, user = null, guestName = "Paolo"),
                MatchPlayer(match = teamMatch, team = teamC, user = elena),
                MatchPlayer(match = teamMatch, team = teamD, user = null, guestName = "Francesca"),
                MatchPlayer(match = teamMatch, team = teamD, user = null, guestName = "Davide"),
                MatchPlayer(match = teamMatch, team = teamE, user = null, guestName = "Sara"),
            ).forEach { matchPlayerRepository.save(it) }

            val inProgressMatch = matchRepository.save(
                Match(
                    game = seti,
                    createdBy = anna,
                    isTeamBased = false,
                    playedAt = Instant.now(),
                    place = null,
                    notes = null,
                    durationMinutes = null,
                )
            )

            listOf(
                MatchPlayer(match = inProgressMatch, user = anna, color = COLOR_1, score = 0, isWinner = false),
                MatchPlayer(match = inProgressMatch, user = luca, color = COLOR_2, score = 0, isWinner = false),
                MatchPlayer(match = inProgressMatch, user = elena, color = COLOR_3, score = 0, isWinner = false),
            ).forEach { matchPlayerRepository.save(it) }

            // Team-based con punteggi, due squadre a pari merito in prima posizione
            val tieMatch = matchRepository.save(
                Match(
                    game = arkNova,
                    createdBy = anna,
                    isTeamBased = true,
                    playedAt = Instant.now().minus(10, ChronoUnit.DAYS),
                    place = "Casa di Anna",
                    notes = "Finale testa a testa, pareggio secco al primo posto.",
                    durationMinutes = 110,
                )
            )
            val tieTeamA = matchTeamRepository.save(
                MatchTeam(match = tieMatch, name = "Falchi", color = COLOR_1, score = 87, isWinner = true, isStartingFirst = true)
            )
            val tieTeamB = matchTeamRepository.save(
                MatchTeam(match = tieMatch, name = "Lupi", color = COLOR_2, score = 87, isWinner = true)
            )
            val tieTeamC = matchTeamRepository.save(
                MatchTeam(match = tieMatch, name = "Orsi", color = COLOR_3, score = 64, isWinner = false)
            )
            listOf(
                MatchPlayer(match = tieMatch, team = tieTeamA, user = anna),
                MatchPlayer(match = tieMatch, team = tieTeamA, user = null, guestName = "Chiara"),
                MatchPlayer(match = tieMatch, team = tieTeamB, user = marco),
                MatchPlayer(match = tieMatch, team = tieTeamB, user = luca),
                MatchPlayer(match = tieMatch, team = tieTeamC, user = elena),
                MatchPlayer(match = tieMatch, team = tieTeamC, user = null, guestName = "Tommaso"),
            ).forEach { matchPlayerRepository.save(it) }

            // Team-based senza punteggio (vittoria/sconfitta), un solo vincitore, squadra singola possibile
            val outcomeMatch = matchRepository.save(
                Match(
                    game = coopGame,
                    createdBy = luca,
                    isTeamBased = true,
                    playedAt = Instant.now().minus(1, ChronoUnit.DAYS).minus(4, ChronoUnit.HOURS),
                    place = "Casa di Luca",
                    notes = "Partita cooperativa contro il gioco, vittoria all'ultimo turno.",
                    durationMinutes = 75,
                )
            )
            val outcomeTeam = matchTeamRepository.save(
                MatchTeam(match = outcomeMatch, name = "Squadra", color = COLOR_1, score = null, isWinner = true, isStartingFirst = true)
            )
            listOf(
                MatchPlayer(match = outcomeMatch, team = outcomeTeam, user = luca),
                MatchPlayer(match = outcomeMatch, team = outcomeTeam, user = anna),
                MatchPlayer(match = outcomeMatch, team = outcomeTeam, user = null, guestName = "Riccardo"),
            ).forEach { matchPlayerRepository.save(it) }

            // Team-based senza punteggio, più squadre vincitrici a pari merito
            val multiWinnerMatch = matchRepository.save(
                Match(
                    game = coopGame,
                    createdBy = elena,
                    isTeamBased = true,
                    playedAt = Instant.now().minus(7, ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS),
                    place = "Ludoteca centrale",
                    notes = "Due squadre hanno raggiunto l'obiettivo insieme, una è stata eliminata.",
                    durationMinutes = 130,
                )
            )
            val multiWinnerTeamA = matchTeamRepository.save(
                MatchTeam(match = multiWinnerMatch, name = "Squadra Rossa", color = COLOR_1, score = null, isWinner = true, isStartingFirst = true)
            )
            val multiWinnerTeamB = matchTeamRepository.save(
                MatchTeam(match = multiWinnerMatch, name = "Squadra Blu", color = COLOR_2, score = null, isWinner = true)
            )
            val multiWinnerTeamC = matchTeamRepository.save(
                MatchTeam(match = multiWinnerMatch, name = "Squadra Verde", color = COLOR_3, score = null, isWinner = false)
            )
            listOf(
                MatchPlayer(match = multiWinnerMatch, team = multiWinnerTeamA, user = elena),
                MatchPlayer(match = multiWinnerMatch, team = multiWinnerTeamA, user = null, guestName = "Noemi"),
                MatchPlayer(match = multiWinnerMatch, team = multiWinnerTeamB, user = marco),
                MatchPlayer(match = multiWinnerMatch, team = multiWinnerTeamB, user = anna),
                MatchPlayer(match = multiWinnerMatch, team = multiWinnerTeamC, user = luca),
            ).forEach { matchPlayerRepository.save(it) }

            logger.info("\n\t[INFO] [match_seeder][seed] 6 demo matches seeded (individual, team-based, in-progress, top-tie, single-winner outcome, multi-winner outcome)")
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_seeder][seed] Error seeding matches: {}", e.message)
            throw e
        }
    }
}