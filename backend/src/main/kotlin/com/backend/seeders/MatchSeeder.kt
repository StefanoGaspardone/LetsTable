package com.backend.seeders

import com.backend.models.entities.*
import com.backend.repositories.MatchPlayerRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.MatchTeamRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class MatchSeeder(
    private val matchRepository: MatchRepository,
    private val matchTeamRepository: MatchTeamRepository,
    private val matchPlayerRepository: MatchPlayerRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun seed(users: List<User>, games: List<Game>) {
        logger.debug("\n\t[DEBUG] [match_seeder][seed] Seeding demo matches")

        try {
            val (marco, anna, luca, elena) = users

            val gamesByBggId = games.associateBy { it.bggId }
            val arkNova = gamesByBggId[342942L] ?: games[0]
            val seti = gamesByBggId[397598L] ?: games[1]
            val twilight = gamesByBggId[233078L] ?: games[3]

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
                MatchPlayer(match = individualMatch, user = marco, color = "#C45135", score = 145, isWinner = true, startingPosition = 1),
                MatchPlayer(match = individualMatch, user = anna, color = "#3B6E91", score = 132, isWinner = false, startingPosition = 2),
                MatchPlayer(match = individualMatch, user = null, guestName = "Giulia", color = "#5C8A4F", score = 98, isWinner = false, startingPosition = 3),
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
                MatchTeam(match = teamMatch, name = "Impero", color = "#C45135", score = 12, isWinner = true, startingPosition = 1)
            )
            val teamB = matchTeamRepository.save(
                MatchTeam(match = teamMatch, name = "Ribelli", color = "#3B6E91", score = 8, isWinner = false, startingPosition = 2)
            )
            val teamC = matchTeamRepository.save(
                MatchTeam(match = teamMatch, name = "Mercanti", color = "#5C8A4F", score = 6, isWinner = false, startingPosition = 3)
            )
            val teamD = matchTeamRepository.save(
                MatchTeam(match = teamMatch, name = "Nomadi", color = "#B08968", score = 9, isWinner = false, startingPosition = 4)
            )
            val teamE = matchTeamRepository.save(
                MatchTeam(match = teamMatch, name = "Federazione", color = "#7C5CBF", score = 5, isWinner = false, startingPosition = 5)
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
                MatchPlayer(match = inProgressMatch, user = anna, color = "#C45135", score = 0, isWinner = false),
                MatchPlayer(match = inProgressMatch, user = luca, color = "#3B6E91", score = 0, isWinner = false),
                MatchPlayer(match = inProgressMatch, user = elena, color = "#5C8A4F", score = 0, isWinner = false),
            ).forEach { matchPlayerRepository.save(it) }

            logger.info("\n\t[INFO] [match_seeder][seed] 3 demo matches seeded (individual, team-based, in-progress)")
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [match_seeder][seed] Error seeding matches: {}", e.message)
            throw e
        }
    }
}