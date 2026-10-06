package com.backend.models.dtos

import com.backend.models.entities.UserAchievement
import com.backend.models.enums.AchievementCategory
import com.backend.models.enums.AchievementTier
import com.backend.models.enums.AchievementType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import java.time.Instant

@Schema(description = "Un traguardo con lo stato di avanzamento dell'utente")
data class UserAchievementDTO(
    @Schema(description = "Codice stabile del traguardo", example = "WINS_10")
    val code: String,

    @Schema(description = "Categoria", example = "WINS")
    val category: AchievementCategory,

    @Schema(description = "Livello, null se il traguardo non ha tier", example = "BRONZE")
    val tier: AchievementTier?,

    @Schema(description = "Titolo", example = "Vincitore seriale")
    val title: String,

    @Schema(description = "Descrizione", example = "Vinci 10 partite")
    val description: String,

    @Schema(description = "Chiave dell'icona", example = "trophy")
    val icon: String,

    @Schema(description = "Valore da raggiungere", example = "10")
    val target: Long,

    @Schema(description = "Progresso attuale, mai oltre il target", example = "7")
    val progress: Long,

    @Schema(description = "Se il traguardo è sbloccato")
    val unlocked: Boolean,

    @Schema(description = "Data di sblocco, null se bloccato")
    val unlockedAt: Instant?,

    @Schema(description = "Se l'utente ha già visto la celebrazione")
    val seen: Boolean,
) {
    companion object {
        fun from(type: AchievementType, metricValue: Long, row: UserAchievement?) = UserAchievementDTO(
            code = type.name,
            category = type.category,
            tier = type.tier,
            title = type.title,
            description = type.description,
            icon = type.icon,
            target = type.target,
            progress = if (row != null) type.target else minOf(metricValue, type.target),
            unlocked = row != null,
            unlockedAt = row?.unlockedAt,
            seen = row?.seenAt != null,
        )
    }
}

@Schema(description = "Traguardi da segnare come visti")
data class MarkAchievementsSeenRequest(
    @field:NotEmpty
    @Schema(description = "Codici dei traguardi visti", example = "[\"FIRST_WIN\", \"WINS_10\"]")
    val codes: List<String>,
)