package ru.reborn.fishing

import java.util.Calendar

/** Daily deterministic conditions: every player sees the same forecast for a given date. */
data class FishingConditions(
    val title: String,
    val biteMultiplier: Double,
    val tensionMultiplier: Float,
    val weightMultiplier: Double
) {
    companion object {
        private val rotation = listOf(
            FishingConditions("ЯСНО", 1.00, 1.00f, 1.00),
            FishingConditions("ОБЛАЧНО", 0.92, 0.96f, 1.02),
            FishingConditions("ДОЖДЬ", 0.82, 1.08f, 1.06),
            FishingConditions("ВЕТЕР", 1.16, 1.14f, 0.96)
        )

        fun forDate(year: Int, dayOfYear: Int): FishingConditions {
            val index = Math.floorMod(year * 31 + dayOfYear * 17, rotation.size)
            return rotation[index]
        }

        fun today(): FishingConditions {
            val calendar = Calendar.getInstance()
            return forDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.DAY_OF_YEAR))
        }
    }
}
