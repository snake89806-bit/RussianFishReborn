package ru.reborn.fishing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishingMarketTest {
    @Test fun dailyPriceIsDeterministic() {
        assertEquals(FishingMarket.price("Карп", 2.5, 2026, 318), FishingMarket.price("Карп", 2.5, 2026, 318))
    }

    @Test fun pricesStayWithinConfiguredMarketRange() {
        for (day in 1..366) {
            val factor = FishingMarket.multiplier("Карп", 2026, day)
            assertTrue(factor in 0.85..1.25)
        }
    }

    @Test fun heavierFishIsWorthMoreOnTheSameDay() {
        assertTrue(FishingMarket.price("Лещ", 2.0, 2026, 100) > FishingMarket.price("Лещ", 1.0, 2026, 100))
    }
}
