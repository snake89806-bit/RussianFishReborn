package ru.reborn.fishing

/** Local tournament rules and scoring. This is a single-device prototype, not an online leaderboard. */
enum class TournamentKind(val title: String, val description: String) {
    HEAVIEST("Трофей недели", "Побеждает самый тяжёлый экземпляр за неделю"),
    TOTAL_WEIGHT("Большой садок", "Учитывается общий вес пойманной рыбы за неделю")
}

data class TournamentState(
    val weekKey: Int,
    val joined: Boolean,
    val catches: Int,
    val totalWeightKg: Double,
    val bestFishName: String,
    val bestWeightKg: Double,
    val bestGrade: TrophyGrade
)

data class TournamentStanding(val title: String, val score: Double, val detail: String)

object TournamentSystem {
    fun currentWeekKey(year: Int, weekOfYear: Int): Int = year * 100 + weekOfYear

    fun fresh(weekKey: Int): TournamentState = TournamentState(
        weekKey = weekKey, joined = false, catches = 0, totalWeightKg = 0.0,
        bestFishName = "—", bestWeightKg = 0.0, bestGrade = TrophyGrade.NONE
    )

    fun join(state: TournamentState): TournamentState = state.copy(joined = true)

    fun recordCatch(
        state: TournamentState,
        fishName: String,
        weightKg: Double,
        grade: TrophyGrade
    ): TournamentState {
        if (!state.joined || weightKg <= 0.0 || !weightKg.isFinite()) return state
        val isBest = weightKg > state.bestWeightKg
        return state.copy(
            catches = state.catches + 1,
            totalWeightKg = state.totalWeightKg + weightKg,
            bestFishName = if (isBest) fishName else state.bestFishName,
            bestWeightKg = maxOf(state.bestWeightKg, weightKg),
            bestGrade = if (isBest) grade else state.bestGrade
        )
    }

    fun standing(state: TournamentState, kind: TournamentKind): TournamentStanding = when (kind) {
        TournamentKind.HEAVIEST -> TournamentStanding("Личный результат", state.bestWeightKg, "${state.bestFishName} • ${"%.2f".format(java.util.Locale.US, state.bestWeightKg)} кг")
        TournamentKind.TOTAL_WEIGHT -> TournamentStanding("Личный результат", state.totalWeightKg, "${state.catches} рыб • ${"%.2f".format(java.util.Locale.US, state.totalWeightKg)} кг")
    }
}
