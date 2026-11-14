package ru.reborn.fishing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishingTimeSeasonTest {
    @Test fun mapsHoursToFourPeriods() {
        assertEquals(FishingTimeSeason.Period.NIGHT, FishingTimeSeason.at(2, 7).period)
        assertEquals(FishingTimeSeason.Period.DAWN, FishingTimeSeason.at(6, 7).period)
        assertEquals(FishingTimeSeason.Period.DAY, FishingTimeSeason.at(12, 7).period)
        assertEquals(FishingTimeSeason.Period.DUSK, FishingTimeSeason.at(19, 7).period)
    }

    @Test fun mapsMonthsToSeasons() {
        assertEquals(FishingTimeSeason.Season.WINTER, FishingTimeSeason.at(12, 1).season)
        assertEquals(FishingTimeSeason.Season.SPRING, FishingTimeSeason.at(12, 4).season)
        assertEquals(FishingTimeSeason.Season.SUMMER, FishingTimeSeason.at(12, 7).season)
        assertEquals(FishingTimeSeason.Season.AUTUMN, FishingTimeSeason.at(12, 10).season)
    }

    @Test fun modifiersAreCombined() {
        val snapshot = FishingTimeSeason.at(6, 7)
        assertTrue(snapshot.biteMultiplier > 0.0)
        assertTrue(snapshot.weightMultiplier > 0.0)
        assertEquals("РАССВЕТ • ЛЕТО", snapshot.label)
    }
}
