package ru.reborn.fishing

import android.content.Context

/** Local admin-console prototype. Real accounts/moderation must be enforced by a trusted server. */
data class AdminPlayer(val id: String, val nickname: String, val coins: Int, val banned: Boolean)

class AdminConsole(context: Context) {
    private val prefs = context.getSharedPreferences("reborn_admin_demo", Context.MODE_PRIVATE)
    var eventEnabled: Boolean
        get() = prefs.getBoolean("event_enabled", false)
        private set(value) { prefs.edit().putBoolean("event_enabled", value).apply() }
    var tournamentEnabled: Boolean
        get() = prefs.getBoolean("tournament_enabled", false)
        private set(value) { prefs.edit().putBoolean("tournament_enabled", value).apply() }
    var announcement: String
        get() = prefs.getString("announcement", "Добро пожаловать на водоём!") ?: ""
        private set(value) { prefs.edit().putString("announcement", value).apply() }

    // Sample records are explicitly local demo data, not connected player accounts.
    private val demoPlayers = listOf("angler_001" to "РечнойВолк", "angler_002" to "Карасик", "angler_003" to "ЩукаПро")
    fun players(): List<AdminPlayer> = demoPlayers.map { (id, nick) ->
        AdminPlayer(id, nick, prefs.getInt("coins_$id", 250), prefs.getBoolean("banned_$id", false))
    }
    fun toggleEvent(): String { eventEnabled = !eventEnabled; return if (eventEnabled) "Ивент включён" else "Ивент выключен" }
    fun toggleTournament(): String { tournamentEnabled = !tournamentEnabled; return if (tournamentEnabled) "Турнир включён" else "Турнир выключен" }
    fun toggleBan(index: Int): String {
        val player = players().getOrNull(index) ?: return "Игрок не найден"
        val value = !player.banned
        prefs.edit().putBoolean("banned_${player.id}", value).apply()
        return if (value) "${player.nickname}: бан (демо)" else "${player.nickname}: разбан (демо)"
    }
    fun grantCoins(index: Int, amount: Int): String {
        val player = players().getOrNull(index) ?: return "Игрок не найден"
        val next = (player.coins + amount).coerceAtMost(2_000_000_000)
        prefs.edit().putInt("coins_${player.id}", next).apply()
        return "${player.nickname}: выдано $amount монет (демо)"
    }
    fun setAnnouncement(value: String): String {
        announcement = value
        return "Объявление обновлено (локально)"
    }
    fun audit(message: String) {
        val old = prefs.getString("audit", "") ?: ""
        val line = "${System.currentTimeMillis()}|$message"
        prefs.edit().putString("audit", (line + "\n" + old).lineSequence().take(30).joinToString("\n")).apply()
    }
    fun auditLines(): List<String> = (prefs.getString("audit", "") ?: "").lines().filter { it.isNotBlank() }
}
