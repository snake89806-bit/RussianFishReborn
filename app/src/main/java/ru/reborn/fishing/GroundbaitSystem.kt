package ru.reborn.fishing

/** Groundbait is a single-cast consumable that shifts the odds toward its target species. */
enum class GroundbaitMix(
    val title: String,
    val pricePerPack: Int,
    val targetSpecies: Set<String>,
    val attraction: Double,
    val description: String
) {
    UNIVERSAL("Универсальная смесь", 15, setOf("Карась серебряный", "Плотва", "Краснопёрка", "Окунь"), 1.8,
        "Для спокойной воды и небольшой рыбы"),
    CARP("Карповая смесь", 22, setOf("Карп", "Линь", "Карась серебряный"), 2.6,
        "Привлекает карпа, линя и крупного карася"),
    DEEP("Речная донная смесь", 25, setOf("Лещ", "Сом", "Судак"), 2.4,
        "Для глубокой воды и донной рыбы")
}

object GroundbaitSystem {
    const val PORTIONS_PER_PACK = 5

    data class Purchase(val stock: Int, val coinsLeft: Int, val purchased: Boolean, val message: String)

    fun buy(currentStock: Int, coins: Int, mix: GroundbaitMix): Purchase {
        if (coins < mix.pricePerPack) return Purchase(currentStock, coins, false, "Не хватает ${mix.pricePerPack} монет")
        return Purchase(currentStock + PORTIONS_PER_PACK, coins - mix.pricePerPack, true,
            "Куплена прикормка «${mix.title}» ×$PORTIONS_PER_PACK")
    }

    fun attraction(species: FishSpecies, mix: GroundbaitMix?): Double =
        if (mix == null) 1.0 else if (species.name in mix.targetSpecies) mix.attraction else 0.72
}
