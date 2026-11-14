package ru.reborn.fishing

import kotlin.random.Random

data class FishSpecies(
    val name: String,
    val minKg: Double,
    val maxKg: Double,
    val difficulty: Float,
    val baits: Set<Int>,
    val locations: Set<Int>
)

/** Original gameplay catalogue. Species ecology is used as a design guide; weights and balance are game values. */
object FishingCatalog {
    val baits = listOf("Червь", "Опарыш", "Кукуруза", "Перловка")
    val locations = listOf("Тихое озеро", "Лесная заводь", "Старая река", "Камышовый плёс", "Большая вода")
    val fish = listOf(
        FishSpecies("Карась серебряный", .12, 1.8, .28f, setOf(0,1,2,3), setOf(0,1,3)),
        FishSpecies("Окунь", .08, 1.4, .42f, setOf(0,1), setOf(0,1,2,4)),
        FishSpecies("Плотва", .05, .9, .22f, setOf(0,1,3), setOf(0,1,2,3)),
        FishSpecies("Лещ", .3, 3.8, .52f, setOf(0,2,3), setOf(0,2,3,4)),
        FishSpecies("Карп", .4, 7.5, .68f, setOf(0,2,3), setOf(0,1,3,4)),
        FishSpecies("Щука", .35, 8.0, .82f, setOf(0,1), setOf(1,2,4)),
        FishSpecies("Линь", .15, 2.6, .48f, setOf(0,2,3), setOf(0,1,3)),
        FishSpecies("Судак", .4, 6.5, .78f, setOf(0,1), setOf(2,4)),
        FishSpecies("Сом", 1.0, 18.0, .95f, setOf(0,2), setOf(2,4)),
        FishSpecies("Краснопёрка", .05, 1.1, .25f, setOf(0,1,3), setOf(0,1,3)),
        FishSpecies("Язь", .25, 3.2, .56f, setOf(0,1,3), setOf(1,2,3,4)),
        FishSpecies("Голавль", .2, 4.5, .64f, setOf(0,1,2), setOf(1,2,4)),
        FishSpecies("Ёрш", .02, .35, .18f, setOf(0,1), setOf(0,2,3,4)),
        FishSpecies("Налим", .4, 7.0, .86f, setOf(0,1,2), setOf(2,4)),
        FishSpecies("Толстолобик", 1.0, 20.0, .88f, setOf(2,3), setOf(0,3,4))
    )

    fun candidates(location: Int, bait: Int): List<FishSpecies> =
        fish.filter { location in it.locations && bait in it.baits }

    fun spotAffinity(species: FishSpecies, spot: Int): Double {
        val preferred = when (spot) {
            0 -> setOf("Окунь", "Плотва", "Краснопёрка", "Карась серебряный")
            1 -> setOf("Карась серебряный", "Карп", "Линь", "Краснопёрка")
            else -> setOf("Лещ", "Щука", "Судак", "Сом", "Карп")
        }
        return if (species.name in preferred) 2.8 else 0.55
    }

    fun choose(location: Int, bait: Int, spot: Int = 0, groundbait: GroundbaitMix? = null): FishSpecies {
        val pool = candidates(location, bait).ifEmpty { fish }
        // Common fish are more likely; selected fishing spot changes the species mix.
        val weights = pool.map { species ->
            val rarity = when (species.difficulty) { in 0f..0.35f -> 6.0; in 0.36f..0.65f -> 3.0; else -> 1.0 }
            rarity * spotAffinity(species, spot) * GroundbaitSystem.attraction(species, groundbait)
        }
        val total = weights.sum()
        var roll = Random.nextDouble(total)
        for (i in pool.indices) { roll -= weights[i]; if (roll <= 0) return pool[i] }
        return pool.last()
    }
}
