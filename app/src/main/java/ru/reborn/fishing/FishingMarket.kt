package ru.reborn.fishing

import java.util.Calendar
import kotlin.math.roundToInt

/** Daily deterministic fish market. Values are game-balance numbers, not real prices. */
object FishingMarket {
    private val basePricePerKg = mapOf(
        "Карась серебряный" to 18, "Окунь" to 24, "Плотва" to 15,
        "Лещ" to 32, "Карп" to 38, "Щука" to 46, "Линь" to 35,
        "Судак" to 54, "Сом" to 62, "Краснопёрка" to 20
    )

    fun multiplier(speciesName: String, year: Int, dayOfYear: Int): Double {
        val index = FishingCatalog.fish.indexOfFirst { it.name == speciesName }.coerceAtLeast(0)
        val seed = Math.floorMod(year * 13 + dayOfYear * 7 + index * 11, 9)
        return 0.85 + seed * 0.05 // 0.85x .. 1.25x
    }

    fun price(speciesName: String, weightKg: Double, year: Int, dayOfYear: Int): Int {
        val base = basePricePerKg[speciesName] ?: 12
        return (base * weightKg.coerceAtLeast(0.0) * multiplier(speciesName, year, dayOfYear)).roundToInt().coerceAtLeast(1)
    }

    fun todayPrice(speciesName: String, weightKg: Double): Int {
        val now = Calendar.getInstance()
        return price(speciesName, weightKg, now.get(Calendar.YEAR), now.get(Calendar.DAY_OF_YEAR))
    }

    fun range(speciesName: String, year: Int, dayOfYear: Int): String =
        "${(multiplier(speciesName, year, dayOfYear) * 100).roundToInt()}%"
}
