package com.backend.models.dtos

import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.MatchTeam
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.validation.annotation.Validated
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Schema(description = "Identity of a player within a team: either a registered user or a guest name")
data class MatchPlayerIdentityRequest(
    @field:Schema(description = "Id of a registered user, if this player has an account")
    val userId: UUID? = null,

    @field:Schema(description = "Display name, if this player does not have an account", example = "Marco")
    val guestName: String? = null,
)

@Schema(description = "A player in a non-team match, with individual color, score and outcome")
data class MatchIndividualPlayerRequest(
    @field:Schema(description = "Id of a registered user, if this player has an account")
    val userId: UUID? = null,

    @field:Schema(description = "Display name, if this player does not have an account", example = "Marco")
    val guestName: String? = null,

    @field:Schema(description = "Player color", example = "red")
    @field:NotBlank
    val color: String,

    @field:Schema(description = "Player score", example = "42")
    val score: Int = 0,

    @field:Schema(description = "Whether this player won the match")
    val isWinner: Boolean = false,

    @field:Schema(description = "Turn order starting position, decided via the finger picker", example = "1")
    val startingPosition: Int? = null,
)

@Schema(description = "A team in a team-based match, with shared color, score and outcome")
data class CreateMatchTeamRequest(
    @field:Schema(description = "Optional team name", example = "Team Red")
    val name: String? = null,

    @field:Schema(description = "Team color", example = "red")
    @field:NotBlank
    val color: String,

    @field:Schema(description = "Team score", example = "42")
    val score: Int = 0,

    @field:Schema(description = "Whether this team won the match")
    val isWinner: Boolean = false,

    @field:Schema(description = "Turn order starting position, decided via the finger picker", example = "1")
    val startingPosition: Int? = null,

    @field:Schema(description = "Players belonging to this team")
    @field:NotEmpty
    @field:Valid
    val players: List<MatchPlayerIdentityRequest>,
)

@Schema(description = "Payload to create or fully replace a match")
data class CreateMatchRequest(
    @field:Schema(description = "Internal id of the game played (obtained via GET /games/{bggId})")
    @field:NotNull
    val gameId: UUID,

    @field:Schema(description = "Date the match was played")
    @field:NotNull
    val playedAt: LocalDate,

    @field:Schema(description = "Where the match was played", example = "Marco's place")
    val place: String? = null,

    @field:Schema(description = "Free-form notes about the match")
    val notes: String? = null,

    @field:Schema(description = "Whether players are grouped into teams")
    @field:NotNull
    val isTeamBased: Boolean,

    @field:Schema(description = "How long the match actually took, in minutes")
    val durationMinutes: Int? = null,

    @field:Schema(description = "Teams, required and non-empty only if isTeamBased is true")
    @field:Valid
    val teams: List<CreateMatchTeamRequest>? = null,

    @field:Schema(description = "Individual players, required and non-empty only if isTeamBased is false")
    @field:Valid
    val players: List<MatchIndividualPlayerRequest>? = null,
)

@Schema(description = "A player's identity within a team (no individual color/score, those are team-level)")
data class MatchPlayerRefDTO(
    @field:Schema(description = "Match player entry id")
    val id: UUID,

    @field:Schema(description = "Public profile, if this player has an account")
    val user: UserDTO?,

    @field:Schema(description = "Display name, if this player does not have an account")
    val guestName: String?,
) {
    companion object {
        fun from(player: MatchPlayer) = MatchPlayerRefDTO(
            id = player.id!!,
            user = player.user?.let { UserDTO.from(it) },
            guestName = player.guestName,
        )
    }
}

@Schema(description = "A team within a team-based match")
data class MatchTeamDTO(
    @field:Schema(description = "Team id")
    val id: UUID,

    @field:Schema(description = "Team name")
    val name: String?,

    @field:Schema(description = "Team color")
    val color: String,

    @field:Schema(description = "Team score")
    val score: Int,

    @field:Schema(description = "Whether this team won")
    val isWinner: Boolean,

    @field:Schema(description = "Turn order starting position")
    val startingPosition: Int?,

    @field:Schema(description = "Players belonging to this team")
    val players: List<MatchPlayerRefDTO>,
) {
    companion object {
        fun from(team: MatchTeam, players: List<MatchPlayerRefDTO>) = MatchTeamDTO(
            id = team.id!!,
            name = team.name,
            color = team.color,
            score = team.score,
            isWinner = team.isWinner,
            players = players,
            startingPosition = team.startingPosition
        )
    }
}

@Schema(description = "A player in a non-team match, with individual color, score and outcome")
data class MatchPlayerDTO(
    @field:Schema(description = "Match player entry id")
    val id: UUID,

    @field:Schema(description = "Public profile, if this player has an account")
    val user: UserDTO?,

    @field:Schema(description = "Display name, if this player does not have an account")
    val guestName: String?,

    @field:Schema(description = "Player color")
    val color: String?,

    @field:Schema(description = "Player score")
    val score: Int?,

    @field:Schema(description = "Whether this player won")
    val isWinner: Boolean?,

    @field:Schema(description = "Turn order starting position")
    val startingPosition: Int?,
) {
    companion object {
        fun from(player: MatchPlayer) = MatchPlayerDTO(
            id = player.id!!,
            user = player.user?.let { UserDTO.from(it) },
            guestName = player.guestName,
            color = player.color,
            score = player.score,
            isWinner = player.isWinner,
            startingPosition = player.startingPosition,
        )
    }
}

@Schema(description = "A logged match, either individual or team-based")
data class MatchDTO(
    @field:Schema(description = "Match id")
    val id: UUID,

    @field:Schema(description = "Game played")
    val game: GameDTO,

    @field:Schema(description = "Public profile of the user who created this match")
    val createdBy: UserDTO,

    @field:Schema(description = "Whether players are grouped into teams")
    val isTeamBased: Boolean,

    @field:Schema(description = "Date the match was played")
    val playedAt: LocalDate,

    @field:Schema(description = "Where the match was played")
    val place: String?,

    @field:Schema(description = "Free-form notes about the match")
    val notes: String?,

    @field:Schema(description = "How long the match actually took, in minutes")
    val durationMinutes: Int?,

    @field:Schema(description = "When the match entry was created")
    val createdAt: Instant,

    @field:Schema(description = "Teams, present only if isTeamBased is true")
    val teams: List<MatchTeamDTO>?,

    @field:Schema(description = "Individual players, present only if isTeamBased is false")
    val players: List<MatchPlayerDTO>?,
) {
    companion object {
        fun from(
            match: Match,
            teams: List<MatchTeamDTO>?,
            players: List<MatchPlayerDTO>?,
        ) = MatchDTO(
            id = match.id!!,
            game = GameDTO.from(match.game),
            createdBy = UserDTO.from(match.createdBy),
            isTeamBased = match.isTeamBased,
            playedAt = match.playedAt,
            place = match.place,
            notes = match.notes,
            createdAt = match.createdAt,
            teams = teams,
            players = players,
            durationMinutes = match.durationMinutes,
        )
    }
}

@Schema(description = "Number of matches played on a specific day")
data class MatchDayCountResponse(
    @field:Schema(description = "The day")
    val date: LocalDate,

    @field:Schema(description = "Number of matches played that day")
    val count: Long,
)