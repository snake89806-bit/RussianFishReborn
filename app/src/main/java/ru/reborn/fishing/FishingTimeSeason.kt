package ru.reborn.fishing

import java.util.Calendar

/** Local-clock fishing cycle and northern-hemisphere seasonal modifiers. */
object FishingTimeSeason {
    enum class Period(val title: String, val biteMultiplier: Double, val weightMultiplier: Double) {
        NIGHT("НОЧЬ", 1.12, 1.02),
        DAWN("РАССВЕТ", 0.82, 1.03),
        DAY("ДЕНЬ", 1.00, 1.00),
        DUSK("ВЕЧЕР", 0.86, 1.04)
    }

    enum class Season(val title: String, val biteMultiplier: Double, val weightMultiplier: Double) {
        WINTER("ЗИМА", 1.18, 0.88),
        SPRING("ВЕСНА", 0.92, 0.96),
        SUMMER("ЛЕТО", 0.88, 1.08),
        AUTUMN("ОСЕНЬ", 0.96, 1.05)
    }

    data class Snapshot(val period: Period, val season: Season, val hour: Int, val month: Int) {
        val biteMultiplier: Double get() = period.biteMultiplier * season.biteMultiplier
        val weightMultiplier: Double get() = period.weightMultiplier * season.weightMultiplier
        val label: String get() = "${period.title} • ${season.title}"
    }

    fun at(hour: Int, month: Int): Snapshot {
        require(hour in 0..23) { "hour must be 0..23" }
        require(month in 1..12) { "month must be 1..12" }
        val period = when (hour) {
            in 5..7 -> Period.DAWN
            in 8..17 -> Period.DAY
            in 18..21 -> Period.DUSK
            else -> Period.NIGHT
        }
        val season = when (month) {
            12, 1, 2 -> Season.WINTER
            in 3..5 -> Season.SPRING
            in 6..8 -> Season.SUMMER
            else -> Season.AUTUMN
        }
        return Snapshot(period, season, hour, month)
    }

    fun now(calendar: Calendar = Calendar.getInstance()): Snapshot =
        at(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MONTH) + 1)
}
