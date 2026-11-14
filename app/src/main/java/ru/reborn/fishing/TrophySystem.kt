package ru.reborn.fishing

import kotlin.math.roundToInt

/** Size-based trophy grades. Thresholds are based on the species' own size range. */
enum class TrophyGrade(val title: String, val multiplier: Double, val code: String) {
    NONE("Обычный улов", 1.0, "none"),
    BRONZE("Бронзовый трофей", 1.10, "bronze"),
    SILVER("Серебряный трофей", 1.20, "silver"),
    GOLD("Золотой трофей", 1.35, "gold"),
    LEGENDARY("Легендарный трофей", 1.60, "legendary")
}

data class TrophyResult(val grade: TrophyGrade, val sizeRatio: Double) {
    val isTrophy: Boolean get() = grade != TrophyGrade.NONE
}

object TrophySystem {
    /** A ratio of 1.0 means the fish reached the species' configured maximum size. */
    fun evaluate(species: FishSpecies, weightKg: Double): TrophyResult {
        val range = (species.maxKg - species.minKg).coerceAtLeast(0.000001)
        val ratio = ((weightKg - species.minKg) / range).coerceIn(0.0, 1.0)
        val grade = when {
            ratio >= 0.997 -> TrophyGrade.LEGENDARY
            ratio >= 0.985 -> TrophyGrade.GOLD
            ratio >= 0.970 -> TrophyGrade.SILVER
            ratio >= 0.900 -> TrophyGrade.BRONZE
            else -> TrophyGrade.NONE
        }
        return TrophyResult(grade, ratio)
    }

    fun saleValue(baseMarketPrice: Int, grade: TrophyGrade): Int =
        (baseMarketPrice.coerceAtLeast(0) * grade.multiplier).roundToInt()
}
