package ru.reborn.fishing

import org.junit.Assert.*
import org.junit.Test

class GroundbaitSystemTest {
    @Test fun buyingAddsFivePortionsAndChargesCoins() {
        val result = GroundbaitSystem.buy(2, 50, GroundbaitMix.CARP)
        assertTrue(result.purchased)
        assertEquals(7, result.stock)
        assertEquals(28, result.coinsLeft)
    }

    @Test fun purchaseFailsWithoutEnoughCoins() {
        val result = GroundbaitSystem.buy(3, 10, GroundbaitMix.UNIVERSAL)
        assertFalse(result.purchased)
        assertEquals(3, result.stock)
        assertEquals(10, result.coinsLeft)
    }

    @Test fun mixAttractsItsTargetSpeciesAndDiscouragesOthers() {
        val carp = FishingCatalog.fish.first { it.name == "Карп" }
        val perch = FishingCatalog.fish.first { it.name == "Окунь" }
        assertTrue(GroundbaitSystem.attraction(carp, GroundbaitMix.CARP) > GroundbaitSystem.attraction(perch, GroundbaitMix.CARP))
        assertEquals(1.0, GroundbaitSystem.attraction(carp, null), 0.0001)
    }
}
