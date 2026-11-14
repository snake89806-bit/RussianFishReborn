package ru.reborn.fishing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishingCatalogTest {
    @Test fun everyLocationAndBaitHasAtLeastOneMatchingSpecies() {
        for (location in FishingCatalog.locations.indices) {
            for (bait in FishingCatalog.baits.indices) {
                assertTrue(
                    "No fish for location=$location bait=$bait",
                    FishingCatalog.fish.any { location in it.locations && bait in it.baits }
                )
            }
        }
    }

    @Test fun speciesHaveValidWeightRangesAndDifficulty() {
        FishingCatalog.fish.forEach { fish ->
            assertTrue(fish.minKg > 0.0)
            assertTrue(fish.maxKg >= fish.minKg)
            assertTrue(fish.difficulty in 0f..1f)
            assertTrue(fish.locations.all { it in FishingCatalog.locations.indices })
            assertTrue(fish.baits.all { it in FishingCatalog.baits.indices })
        }
    }

    @Test fun everySpotHasPositiveAffinityForEverySpecies() {
        FishingCatalog.fish.forEach { species ->
            for (spot in 0..2) assertTrue(FishingCatalog.spotAffinity(species, spot) > 0.0)
        }
    }

    @Test fun preferredSpeciesHaveHigherChanceAtTheirSpot() {
        val perch = FishingCatalog.fish.first { it.name == "Окунь" }
        val catfish = FishingCatalog.fish.first { it.name == "Сом" }
        assertTrue(FishingCatalog.spotAffinity(perch, 0) > FishingCatalog.spotAffinity(perch, 2))
        assertTrue(FishingCatalog.spotAffinity(catfish, 2) > FishingCatalog.spotAffinity(catfish, 0))
    }

    @Test fun catalogueHasExpectedPlayableContent() {
        assertEquals(5, FishingCatalog.locations.size)
        assertEquals(4, FishingCatalog.baits.size)
        assertEquals(10, FishingCatalog.fish.size)
    }
}
