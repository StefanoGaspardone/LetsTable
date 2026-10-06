package com.backend.models.enums

enum class AchievementCategory { MATCHES, WINS, STREAK, COLLECTION, SOCIAL }

enum class AchievementTier { BRONZE, SILVER, GOLD }

enum class AchievementMetric {
    MATCHES_PLAYED, MATCHES_WON, MATCHES_LOST, DISTINCT_GAMES_PLAYED,
    WIN_STREAK, WEEKLY_PLAY_STREAK, COLLECTION_SIZE, FRIENDS_COUNT
}

enum class AchievementType(
    val category: AchievementCategory,
    val metric: AchievementMetric,
    val target: Long,
    val tier: AchievementTier?,
    val title: String,
    val description: String,
    val icon: String,
) {
    FIRST_MATCH(AchievementCategory.MATCHES, AchievementMetric.MATCHES_PLAYED, 1, null,
        "Si comincia", "Concludi la tua prima partita", "dices"),
    MATCHES_10(AchievementCategory.MATCHES, AchievementMetric.MATCHES_PLAYED, 10, AchievementTier.BRONZE,
        "Giocatore abituale", "Concludi 10 partite", "dices"),
    MATCHES_50(AchievementCategory.MATCHES, AchievementMetric.MATCHES_PLAYED, 50, AchievementTier.SILVER,
        "Habitué del tavolo", "Concludi 50 partite", "dices"),
    MATCHES_100(AchievementCategory.MATCHES, AchievementMetric.MATCHES_PLAYED, 100, AchievementTier.GOLD,
        "Leggenda del tavolo", "Concludi 100 partite", "dices"),
    DISTINCT_GAMES_10(AchievementCategory.MATCHES, AchievementMetric.DISTINCT_GAMES_PLAYED, 10, null,
        "Esploratore", "Gioca 10 giochi diversi", "compass"),

    FIRST_WIN(AchievementCategory.WINS, AchievementMetric.MATCHES_WON, 1, null,
        "Prima gioia", "Vinci la tua prima partita", "trophy"),
    WINS_10(AchievementCategory.WINS, AchievementMetric.MATCHES_WON, 10, AchievementTier.BRONZE,
        "Vincitore seriale", "Vinci 10 partite", "trophy"),
    WINS_50(AchievementCategory.WINS, AchievementMetric.MATCHES_WON, 50, AchievementTier.GOLD,
        "Campione", "Vinci 50 partite", "trophy"),
    LOSSES_10(AchievementCategory.WINS, AchievementMetric.MATCHES_LOST, 10, null,
        "Perdente di classe", "Perdi 10 partite. L'importante è partecipare!", "frown"),

    WIN_STREAK_3(AchievementCategory.STREAK, AchievementMetric.WIN_STREAK, 3, AchievementTier.BRONZE,
        "Tripletta", "Vinci 3 partite di fila", "flame"),
    WIN_STREAK_5(AchievementCategory.STREAK, AchievementMetric.WIN_STREAK, 5, AchievementTier.GOLD,
        "Inarrestabile", "Vinci 5 partite di fila", "flame"),
    WEEK_STREAK_4(AchievementCategory.STREAK, AchievementMetric.WEEKLY_PLAY_STREAK, 4, AchievementTier.BRONZE,
        "Un mese di tavolo", "Gioca per 4 settimane consecutive", "calendar-check"),
    WEEK_STREAK_8(AchievementCategory.STREAK, AchievementMetric.WEEKLY_PLAY_STREAK, 8, AchievementTier.GOLD,
        "Rito settimanale", "Gioca per 8 settimane consecutive", "calendar-check"),

    COLLECTION_10(AchievementCategory.COLLECTION, AchievementMetric.COLLECTION_SIZE, 10, AchievementTier.BRONZE,
        "Collezionista", "Avere 10 giochi in collezione", "library"),
    COLLECTION_50(AchievementCategory.COLLECTION, AchievementMetric.COLLECTION_SIZE, 50, AchievementTier.GOLD,
        "Ludoteca privata", "Avere 50 giochi in collezione", "library"),

    FIRST_FRIEND(AchievementCategory.SOCIAL, AchievementMetric.FRIENDS_COUNT, 1, null,
        "Non più solo", "Aggiungi il tuo primo amico", "user-plus"),
    FRIENDS_5(AchievementCategory.SOCIAL, AchievementMetric.FRIENDS_COUNT, 5, AchievementTier.SILVER,
        "Gruppo di gioco", "Avere 5 amici", "users"),
}