package ru.reborn.fishing

import org.junit.Assert.*
import org.junit.Test

class TournamentSystemTest {
    @Test fun tournamentStartsEmptyAndRequiresJoining() {
        val state = TournamentSystem.fresh(202647)
        val unchanged = TournamentSystem.recordCatch(state, "Карп", 4.2, TrophyGrade.GOLD)
        assertEquals(0, unchanged.catches)
        assertFalse(unchanged.joined)
        assertTrue(TournamentSystem.join(state).joined)
    }

    @Test fun recordsCatchAndKeepsHeaviestFish() {
        val joined = TournamentSystem.join(TournamentSystem.fresh(202647))
        val first = TournamentSystem.recordCatch(joined, "Карп", 3.2, TrophyGrade.SILVER)
        val second = TournamentSystem.recordCatch(first, "Сом", 7.4, TrophyGrade.GOLD)
        val third = TournamentSystem.recordCatch(second, "Окунь", 0.7, TrophyGrade.NONE)
        assertEquals(3, third.catches)
        assertEquals(11.3, third.totalWeightKg, 0.0001)
        assertEquals("Сом", third.bestFishName)
        assertEquals(7.4, third.bestWeightKg, 0.0001)
        assertEquals(TrophyGrade.GOLD, third.bestGrade)
    }

    @Test fun standingsSwitchBetweenBestFishAndTotalWeight() {
        val state = TournamentSystem.recordCatch(
            TournamentSystem.join(TournamentSystem.fresh(202647)), "Щука", 5.0, TrophyGrade.BRONZE
        )
        assertEquals(5.0, TournamentSystem.standing(state, TournamentKind.HEAVIEST).score, 0.0001)
        assertEquals(5.0, TournamentSystem.standing(state, TournamentKind.TOTAL_WEIGHT).score, 0.0001)
    }

    @Test fun invalidCatchDoesNotChangeState() {
        val state = TournamentSystem.join(TournamentSystem.fresh(202647))
        assertEquals(state, TournamentSystem.recordCatch(state, "Сом", -1.0, TrophyGrade.NONE))
        assertEquals(state, TournamentSystem.recordCatch(state, "Сом", Double.NaN, TrophyGrade.NONE))
    }
}
