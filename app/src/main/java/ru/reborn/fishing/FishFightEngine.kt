package ru.reborn.fishing

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Deterministic fight model. A fish alternates between calm movement, bursts and fatigue. */
class FishFightEngine(
    private val difficulty: Float,
    rodLevel: Int,
    reelLevel: Int,
    lineLevel: Int,
    private val weatherTensionMultiplier: Float,
    hookEfficiency: Float = 1f,
    lineStrengthMultiplier: Float = 1f
) {
    enum class Result { ACTIVE, CAUGHT, LINE_BROKE, ESCAPED }
    enum class Behavior { CALM, SURGING, TIRED }

    private val skill = difficulty.coerceIn(0.1f, 1f)
    private val rodControl = (1f - (rodLevel.coerceIn(1, 5) - 1) * 0.055f).coerceAtLeast(0.72f)
    private val reelPower = 1f + (reelLevel.coerceIn(1, 5) - 1) * 0.11f
    private val lineLimit = (1f + (lineLevel.coerceIn(1, 5) - 1) * 0.12f) * lineStrengthMultiplier.coerceIn(.8f, 1.5f)
    private val hookPower = hookEfficiency.coerceIn(.8f, 1.3f)
    private val stamina = 42f + skill * 42f

    var elapsedSeconds: Float = 0f
        private set
    var tension: Float = 0.28f
        private set
    var progress: Float = 0f
        private set
    var fatigue: Float = 0f
        private set
    var isFishSurging: Boolean = false
        private set
    var behavior: Behavior = Behavior.CALM
        private set
    private var slackSeconds = 0f

    /** Returns terminal result or ACTIVE. Delta is clamped to keep simulation stable on frame drops. */
    fun update(deltaSeconds: Float, reeling: Boolean): Result {
        val dt = deltaSeconds.coerceIn(0f, 0.1f)
        elapsedSeconds += dt

        // Stronger fish surge more often. As stamina drains, bursts become shorter and weaker.
        val wave = sin(elapsedSeconds * (1.15f + skill * 2.0f))
        val staminaRatio = (1f - fatigue / stamina).coerceIn(0f, 1f)
        val surgeThreshold = 0.78f - skill * 0.12f + (1f - staminaRatio) * 0.18f
        isFishSurging = wave > surgeThreshold && staminaRatio > 0.12f
        behavior = when {
            isFishSurging -> Behavior.SURGING
            staminaRatio <= 0.12f -> Behavior.TIRED
            else -> Behavior.CALM
        }

        if (reeling) {
            val surgeForce = if (isFishSurging) (0.12f + skill * 0.16f) * staminaRatio else 0f
            val pull = 0.11f + skill * 0.13f + surgeForce
            tension += dt * pull * rodControl * weatherTensionMultiplier
            val reelGain = (0.105f + (reelPower - 1f) * 0.11f) * hookPower
            val resistance = skill * 0.052f + surgeForce * 0.25f
            progress += dt * (reelGain - resistance).coerceAtLeast(0.025f)
            fatigue += dt * (0.22f + reelPower * 0.12f)
            slackSeconds = 0f
        } else {
            tension -= dt * (0.19f + rodControl * 0.025f)
            progress -= dt * (0.008f + skill * 0.012f)
            fatigue = max(0f, fatigue - dt * 0.08f)
            if (tension < 0.10f) slackSeconds += dt else slackSeconds = 0f
        }

        tension = tension.coerceIn(0f, lineLimit + 0.3f)
        progress = progress.coerceIn(0f, 1f)
        fatigue = fatigue.coerceIn(0f, stamina)

        return when {
            tension >= lineLimit -> Result.LINE_BROKE
            progress >= 1f -> Result.CAUGHT
            slackSeconds >= 4.5f || elapsedSeconds >= 95f -> Result.ESCAPED
            else -> Result.ACTIVE
        }
    }
}
