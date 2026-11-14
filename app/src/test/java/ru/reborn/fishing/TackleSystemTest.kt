package ru.reborn.fishing

import org.junit.Assert.*
import org.junit.Test

class TackleSystemTest {
    @Test fun defaultRigIsFreeAndUsable() {
        val rig = TackleSystem.Loadout()
        assertEquals("Пробковый", rig.option(TacklePart.FLOAT).name)
        assertEquals(1f, rig.lineStrength, .0001f)
    }

    @Test fun componentUpgradeChargesCoinsAndChangesOnlySelectedPart() {
        val result = TackleSystem.upgrade(TackleSystem.Loadout(), TacklePart.LINE, 100)
        assertTrue(result.changed)
        assertEquals(55, result.coinsLeft)
        assertEquals(1, result.loadout.lineIndex)
        assertEquals(0, result.loadout.hookIndex)
    }

    @Test fun cannotEquipAnUnaffordableComponent() {
        val original = TackleSystem.Loadout()
        val result = TackleSystem.upgrade(original, TacklePart.LINE, 10)
        assertFalse(result.changed)
        assertEquals(original, result.loadout)
        assertEquals(10, result.coinsLeft)
    }

    @Test fun bestComponentsImproveTheRelevantStats() {
        var rig = TackleSystem.Loadout()
        for (part in TacklePart.values()) {
            while (TackleSystem.upgrade(rig, part, 10_000).changed) {
                rig = TackleSystem.upgrade(rig, part, 10_000).loadout
            }
        }
        assertTrue(rig.biteDelayMultiplier < TackleSystem.Loadout().biteDelayMultiplier)
        assertTrue(rig.hookEfficiency > TackleSystem.Loadout().hookEfficiency)
        assertTrue(rig.lineStrength > TackleSystem.Loadout().lineStrength)
    }
}
