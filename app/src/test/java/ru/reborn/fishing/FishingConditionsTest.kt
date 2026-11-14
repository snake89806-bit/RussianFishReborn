package ru.reborn.fishing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishingConditionsTest {
    @Test fun forecastIsStableForTheSameDate() {
        assertEquals(FishingConditions.forDate(2026, 318), FishingConditions.forDate(2026, 318))
    }

    @Test fun forecastUsesOnlySupportedConditions() {
        val allowed = setOf("ЯСНО", "ОБЛАЧНО", "ДОЖДЬ", "ВЕТЕР")
        for (day in 1..366) {
            val weather = FishingConditions.forDate(2026, day)
            assertTrue(weather.title in allowed)
            assertTrue(weather.biteMultiplier in 0.5..2.0)
            assertTrue(weather.tensionMultiplier in 0.5f..2.0f)
            assertTrue(weather.weightMultiplier in 0.5..2.0)
        }
    }

    @Test fun allFourWeatherTypesOccurAcrossTheYear() {
        val seen = (1..366).map { FishingConditions.forDate(2026, it).title }.toSet()
        assertEquals(4, seen.size)
    }
}
