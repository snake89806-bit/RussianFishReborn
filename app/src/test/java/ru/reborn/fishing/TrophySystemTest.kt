package ru.reborn.fishing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrophySystemTest {
    private val carp = FishingCatalog.fish.first { it.name == "Карп" }

    @Test fun ordinaryFishIsNotTrophy() {
        assertEquals(TrophyGrade.NONE, TrophySystem.evaluate(carp, 2.0).grade)
    }

    @Test fun gradesUseSpeciesRelativeSize() {
        val range = carp.maxKg - carp.minKg
        assertEquals(TrophyGrade.BRONZE, TrophySystem.evaluate(carp, carp.minKg + range * .92).grade)
        assertEquals(TrophyGrade.SILVER, TrophySystem.evaluate(carp, carp.minKg + range * .975).grade)
        assertEquals(TrophyGrade.GOLD, TrophySystem.evaluate(carp, carp.minKg + range * .99).grade)
        assertEquals(TrophyGrade.LEGENDARY, TrophySystem.evaluate(carp, carp.maxKg).grade)
    }

    @Test fun trophyValueMultiplierIncreasesByGrade() {
        assertTrue(TrophyGrade.BRONZE.multiplier > TrophyGrade.NONE.multiplier)
        assertTrue(TrophyGrade.LEGENDARY.multiplier > TrophyGrade.GOLD.multiplier)
        assertEquals(160, TrophySystem.saleValue(100, TrophyGrade.LEGENDARY))
        assertEquals(100, TrophySystem.saleValue(100, TrophyGrade.NONE))
    }

    @Test fun sizeRatioIsClamped() {
        assertEquals(0.0, TrophySystem.evaluate(carp, 0.0).sizeRatio, 0.0001)
        assertEquals(1.0, TrophySystem.evaluate(carp, 100.0).sizeRatio, 0.0001)
    }
}
