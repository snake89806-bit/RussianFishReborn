package ru.reborn.fishing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishFightEngineTest {
    @Test fun holdingReelBuildsProgressAndTension() {
        val engine = FishFightEngine(.45f, 1, 1, 1, 1f)
        repeat(20) { engine.update(.05f, true) }
        assertTrue(engine.progress > 0f)
        assertTrue(engine.tension > .28f)
    }

    @Test fun releasingReelReducesTension() {
        val engine = FishFightEngine(.4f, 1, 1, 3, 1f)
        repeat(10) { engine.update(.05f, true) }
        val before = engine.tension
        repeat(10) { engine.update(.05f, false) }
        assertTrue(engine.tension < before)
    }

    @Test fun veryLongSlackLetsFishEscape() {
        val engine = FishFightEngine(.3f, 1, 1, 1, 1f)
        var result = FishFightEngine.Result.ACTIVE
        repeat(160) { result = engine.update(.05f, false) }
        assertEquals(FishFightEngine.Result.ESCAPED, result)
    }

    @Test fun strongerLineSurvivesLongerUnderTheSamePull() {
        fun secondsUntilBreak(lineLevel: Int): Float {
            val engine = FishFightEngine(.9f, 1, 1, lineLevel, 1.2f)
            var result = FishFightEngine.Result.ACTIVE
            var frames = 0
            while (result == FishFightEngine.Result.ACTIVE && frames < 200) {
                result = engine.update(.05f, true)
                frames++
            }
            assertEquals(FishFightEngine.Result.LINE_BROKE, result)
            return frames * .05f
        }
        assertTrue(secondsUntilBreak(5) > secondsUntilBreak(1))
    }

    @Test fun hardFightingFishCanEnterSurgeState() {
        val engine = FishFightEngine(.95f, 1, 1, 5, 1f)
        var sawSurge = false
        repeat(120) {
            engine.update(.05f, false)
            if (engine.behavior == FishFightEngine.Behavior.SURGING) sawSurge = true
        }
        assertTrue(sawSurge)
    }

    @Test fun exhaustingFishEventuallyBecomesTired() {
        val engine = FishFightEngine(.7f, 5, 5, 5, .8f)
        repeat(4000) {
            if (engine.update(.05f, true) == FishFightEngine.Result.ACTIVE) Unit
        }
        assertTrue(engine.fatigue > 0f)
        assertEquals(FishFightEngine.Behavior.TIRED, engine.behavior)
        assertTrue(engine.fatigue <= 71.5f)
    }

    @Test fun deltaIsClampedAfterLongFramePause() {
        val normal = FishFightEngine(.5f, 1, 1, 5, 1f)
        val paused = FishFightEngine(.5f, 1, 1, 5, 1f)
        normal.update(.1f, true)
        paused.update(5f, true)
        assertEquals(normal.progress, paused.progress, .0001f)
        assertEquals(normal.tension, paused.tension, .0001f)
    }
}
