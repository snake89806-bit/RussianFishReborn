package ru.reborn.fishing

/** Components of a player-assembled fishing rig. Values are game-balance modifiers. */
enum class TacklePart(val title: String) { FLOAT("Поплавок"), HOOK("Крючок"), SINKER("Грузило"), LINE("Поводочная оснастка") }

data class TackleOption(
    val id: String,
    val name: String,
    val price: Int,
    val biteDelayMultiplier: Float = 1f,
    val hookEfficiency: Float = 1f,
    val lineStrength: Float = 1f,
    val description: String
)

object TackleSystem {
    val options: Map<TacklePart, List<TackleOption>> = mapOf(
        TacklePart.FLOAT to listOf(
            TackleOption("float_cork", "Пробковый", 0, biteDelayMultiplier = 1.08f, description = "Простой и заметный поплавок"),
            TackleOption("float_feather", "Перьевой", 35, biteDelayMultiplier = .94f, description = "Чувствительнее показывает осторожную поклёвку"),
            TackleOption("float_waggler", "Ваглер", 90, biteDelayMultiplier = .86f, description = "Для дальнего заброса и слабой поклёвки")
        ),
        TacklePart.HOOK to listOf(
            TackleOption("hook_basic", "Обычный №8", 0, hookEfficiency = .96f, description = "Универсальный крючок"),
            TackleOption("hook_fine", "Тонкий №12", 28, hookEfficiency = 1.04f, description = "Лучше для небольшой мирной рыбы"),
            TackleOption("hook_forged", "Кованый №4", 75, hookEfficiency = 1.14f, description = "Надёжнее держит крупную рыбу")
        ),
        TacklePart.SINKER to listOf(
            TackleOption("sinker_split", "Дробинка 2 г", 0, biteDelayMultiplier = 1.03f, description = "Лёгкая оснастка для мелководья"),
            TackleOption("sinker_olivette", "Оливка 8 г", 32, biteDelayMultiplier = .98f, description = "Универсальный груз для течения"),
            TackleOption("sinker_sliding", "Скользящий 18 г", 68, biteDelayMultiplier = .93f, description = "Стабильнее держит глубокую точку")
        ),
        TacklePart.LINE to listOf(
            TackleOption("line_mono", "Мононить 0,18", 0, lineStrength = 1f, description = "Мягкая базовая леска"),
            TackleOption("line_mono_thick", "Мононить 0,25", 45, lineStrength = 1.12f, description = "Больше запас прочности"),
            TackleOption("line_braid", "Плетёнка 0,16", 110, lineStrength = 1.28f, description = "Высокая прочность для трофеев")
        )
    )

    data class Loadout(
        val floatIndex: Int = 0,
        val hookIndex: Int = 0,
        val sinkerIndex: Int = 0,
        val lineIndex: Int = 0
    ) {
        fun index(part: TacklePart): Int = when (part) {
            TacklePart.FLOAT -> floatIndex
            TacklePart.HOOK -> hookIndex
            TacklePart.SINKER -> sinkerIndex
            TacklePart.LINE -> lineIndex
        }
        fun option(part: TacklePart): TackleOption = options.getValue(part)[index(part).coerceIn(options.getValue(part).indices)]
        fun withIndex(part: TacklePart, index: Int): Loadout = when (part) {
            TacklePart.FLOAT -> copy(floatIndex = index)
            TacklePart.HOOK -> copy(hookIndex = index)
            TacklePart.SINKER -> copy(sinkerIndex = index)
            TacklePart.LINE -> copy(lineIndex = index)
        }
        val biteDelayMultiplier: Float get() = (option(TacklePart.FLOAT).biteDelayMultiplier * option(TacklePart.SINKER).biteDelayMultiplier).coerceIn(.75f, 1.2f)
        val hookEfficiency: Float get() = option(TacklePart.HOOK).hookEfficiency
        val lineStrength: Float get() = option(TacklePart.LINE).lineStrength
    }

    data class PurchaseResult(val loadout: Loadout, val coinsLeft: Int, val changed: Boolean, val message: String)

    /** Advances one component to the next model. A component is equipped only after payment. */
    fun upgrade(loadout: Loadout, part: TacklePart, coins: Int): PurchaseResult {
        val list = options.getValue(part)
        val next = loadout.index(part) + 1
        if (next !in list.indices) return PurchaseResult(loadout, coins, false, "Для ${part.title.lowercase()} уже установлен лучший вариант")
        val item = list[next]
        if (coins < item.price) return PurchaseResult(loadout, coins, false, "Нужно ${item.price} монет для «${item.name}»")
        return PurchaseResult(loadout.withIndex(part, next), coins - item.price, true, "Установлено: ${item.name}")
    }
}
