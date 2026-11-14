package ru.reborn.fishing.online

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Small HTTPS-only REST client. All calls run off the UI thread. */
class OnlineApi(baseUrl: String) {
    private val root = baseUrl.trimEnd('/')
    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    init {
        require(root.startsWith("https://")) { "Для онлайн-режима требуется HTTPS-адрес сервера" }
    }

    data class Result(val ok: Boolean, val status: Int, val body: JSONObject?, val error: String?)

    fun register(username: String, displayName: String, password: String, done: (Result) -> Unit) = request(
        "POST", "/v1/register", null,
        JSONObject().put("username", username).put("displayName", displayName).put("password", password), done
    )

    fun login(username: String, password: String, done: (Result) -> Unit) = request(
        "POST", "/v1/login", null,
        JSONObject().put("username", username).put("password", password), done
    )

    fun me(token: String, done: (Result) -> Unit) = request("GET", "/v1/me", token, null, done)

    fun leaderboard(done: (Result) -> Unit) = request("GET", "/v1/leaderboard?limit=50", null, null, done)
    fun joinTournament(token: String, done: (Result) -> Unit) = request("POST", "/v1/tournaments/join", token, JSONObject(), done)
    fun tournamentLeaderboard(token: String, done: (Result) -> Unit) = request("GET", "/v1/tournaments/leaderboard", token, null, done)

    fun submitCatch(token: String, eventId: String, fishId: String, weightKg: Double,
                    locationId: String, caughtAtIso: String, done: (Result) -> Unit) = request(
        "POST", "/v1/catches", token,
        JSONObject().put("clientEventId", eventId).put("fishId", fishId)
            .put("weightKg", weightKg).put("locationId", locationId).put("caughtAt", caughtAtIso), done
    )

    // Owner/admin API. The server checks the current account role on every request.
    fun adminUsers(token: String, done: (Result) -> Unit) = request("GET", "/v1/admin/users", token, null, done)
    fun adminAudit(token: String, done: (Result) -> Unit) = request("GET", "/v1/admin/audit", token, null, done)
    fun grantCoins(token: String, userId: String, amount: Int, done: (Result) -> Unit) = request(
        "POST", "/v1/admin/users/${userId}/coins", token, JSONObject().put("amount", amount), done
    )
    fun setEvents(token: String, enabled: Boolean, title: String, message: String, done: (Result) -> Unit) = request(
        "PUT", "/v1/admin/events", token,
        JSONObject().put("enabled", enabled).put("title", title).put("message", message), done
    )
    fun setTournaments(token: String, enabled: Boolean, title: String, message: String, done: (Result) -> Unit) = request(
        "PUT", "/v1/admin/tournaments", token,
        JSONObject().put("enabled", enabled).put("title", title).put("message", message), done
    )
    fun announce(token: String, title: String, message: String, done: (Result) -> Unit) = request(
        "POST", "/v1/admin/announcements", token,
        JSONObject().put("title", title).put("message", message), done
    )
    fun setBan(token: String, userId: String, banned: Boolean, done: (Result) -> Unit) = request(
        "POST", "/v1/admin/users/${userId}/${if (banned) "ban" else "unban"}", token, JSONObject(), done
    )
    fun features(done: (Result) -> Unit) = request("GET", "/v1/features", null, null, done)
    fun announcements(done: (Result) -> Unit) = request("GET", "/v1/announcements", null, null, done)

    private fun request(method: String, path: String, token: String?, payload: JSONObject?, done: (Result) -> Unit) {
        io.execute {
            val result = try {
                val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 8_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                    token?.let { setRequestProperty("Authorization", "Bearer $it") }
                    if (payload != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                }
                try {
                    if (payload != null) connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val text = stream?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() }.orEmpty()
                    val body = if (text.isBlank()) null else JSONObject(text)
                    Result(status in 200..299, status, body, body?.optString("error")?.takeIf { it.isNotBlank() })
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                Result(false, 0, null, e.message ?: "Ошибка соединения")
            }
            main.post { done(result) }
        }
    }

    fun close() { io.shutdown() }
}
