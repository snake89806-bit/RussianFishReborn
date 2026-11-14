package ru.reborn.fishing

import android.content.Context
import android.app.AlertDialog
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.os.Handler
import android.os.Looper
import kotlin.math.*
import kotlin.random.Random

/** First playable vertical slice. All artwork is drawn procedurally; no third-party game assets are bundled. */
class FishingGameView(context: Context) : View(context) {
    private enum class Phase { READY, WAITING, BITE, FIGHT, CAUGHT, SHOP, BAG, JOURNAL, ACHIEVEMENTS, QUESTS, MAP, SPOTS, ADMIN, TACKLE, GROUNDBAIT, TOURNAMENT, ONLINE, ADMIN_ONLINE, WINTER_EVENT }
    private var phase = Phase.READY
    private val prefs = context.getSharedPreferences("reborn_save", Context.MODE_PRIVATE)
    private val admin = AdminConsole(context)
    private val sessionStore = ru.reborn.fishing.online.SessionStore(context)
    private var onlineApi: ru.reborn.fishing.online.OnlineApi? = null
    private var onlineStatus = "Не подключено"
    private var onlineProfile = "Войдите или создайте аккаунт"
    private var onlineLeaderboard = emptyList<String>()
    private var remoteEventEnabled = false
    private var remoteEventTitle = ""
    private var remoteEventMessage = ""
    private var remoteTournamentEnabled = false
    private var remoteTournamentTitle = ""
    private var remoteTournamentMessage = ""
    private var onlineTournamentJoined = false
    private var onlineTournamentBoard = emptyList<String>()
    private var onlineTournamentStatus = "Войдите в аккаунт для участия"
    private var onlineAnnouncements = emptyList<String>()
    private var onlineBusy = false
    private var winterEventBanner: Bitmap? = null
    private var winterEventKey = prefs.getString("winter_event_key", "") ?: ""
    private var winterEventUpdated = prefs.getString("winter_event_updated", "") ?: ""
    private var winterCatchLocations = (prefs.getStringSet("winter_catch_locations", emptySet()) ?: emptySet()).toMutableSet()
    private var winterPikeCount = prefs.getInt("winter_pike_count", 0)
    private var winterTrophyCount = prefs.getInt("winter_trophy_count", 0)
    private var winterClaimed = (0..2).map { prefs.getBoolean("winter_claim_$it", false) }.toMutableList()
    private var onlineIsAdmin = false
    private var adminOnlineStatus = "Панель доступна только администратору"
    private var adminMessage = "Локальная демонстрация: онлайн-сервер не подключён"
    private var adminSelectedPlayer = 0
    private var coins = prefs.getInt("coins", 120)
    private var xp = prefs.getInt("xp", 0)
    private var catches = prefs.getInt("catches", 0)
    private var location = prefs.getInt("location", 0).coerceIn(0, 4)
    private var fishingSpot = prefs.getInt("fishing_spot", 0).coerceIn(0, 2)
    private val fishingSpots = listOf("Открытая вода", "Камышовая кромка", "Глубокая яма")
    private var selectedBait = prefs.getInt("bait", 0)
    private val groundbaitStock = IntArray(GroundbaitMix.values().size) { i -> prefs.getInt("groundbait_stock_$i", 0) }
    private var activeGroundbait: GroundbaitMix? = null
    private var selectedGroundbait = prefs.getInt("selected_groundbait", -1).coerceIn(-1, GroundbaitMix.values().lastIndex)
    private var rodLevel = prefs.getInt("rod_level", 1).coerceIn(1, 5)
    private var reelLevel = prefs.getInt("reel_level", 1).coerceIn(1, 5)
    private var lineLevel = prefs.getInt("line_level", 1).coerceIn(1, 5)
    private var tackle = TackleSystem.Loadout(
        floatIndex = prefs.getInt("tackle_float", 0).coerceIn(0, 2),
        hookIndex = prefs.getInt("tackle_hook", 0).coerceIn(0, 2),
        sinkerIndex = prefs.getInt("tackle_sinker", 0).coerceIn(0, 2),
        lineIndex = prefs.getInt("tackle_line", 0).coerceIn(0, 2)
    )
    private val baitStock = IntArray(4) { i -> prefs.getInt("bait_stock_$i", if (i == 0) 20 else 0) }
    private var castAt = 0L
    private var biteAt = 0L
    private val conditions = FishingConditions.today()
    private var nextBiteDelay = newBiteDelay()
    private var currentFish: FishSpecies? = null
    private var fightTime = 0f
    private var tension = 0.24f
    private var fishProgress = 0f
    private var fightEngine: FishFightEngine? = null
    private var fishName = "Карась"
    private var fishWeight = 0.0
    private var lastCatchName = prefs.getString("last_catch_name", "Пока нет улова") ?: "Пока нет улова"
    private var lastCatchWeight = prefs.getFloat("last_catch_weight", 0f).toDouble()
    private var lastCatchValue = prefs.getInt("last_catch_value", 0)
    private var lastTrophyGrade = TrophyGrade.values().firstOrNull { it.code == prefs.getString("last_trophy_grade", "none") } ?: TrophyGrade.NONE
    private var message = "Выберите место и забросьте удочку"
    private val dayKey = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.YEAR) * 1000L + it.get(java.util.Calendar.DAY_OF_YEAR) }
    private var dailyCatches = 0
    private var dailySales = 0
    private var dailyVisited = mutableSetOf<String>()
    private var tournamentKind = TournamentKind.HEAVIEST
    private var tournamentState = loadTournamentState()
    private var touchDownAt = 0L
    private var reeling = false
    private var lastFrame = System.currentTimeMillis()
    private var bobberY = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    private val locations = FishingCatalog.locations
    private val baits = FishingCatalog.baits

    init {
        isFocusable = true
        winterEventBanner = runCatching { android.graphics.BitmapFactory.decodeResource(resources, ru.reborn.fishing.R.drawable.winter_event_banner) }.getOrNull()
        val savedDay = prefs.getLong("daily_day", -1L)
        if (savedDay != dayKey) {
            prefs.edit().putLong("daily_day", dayKey).putInt("daily_catches", 0)
                .putInt("daily_sales", 0).putStringSet("daily_visited", emptySet())
                .putBoolean("daily_claim_catch", false).putBoolean("daily_claim_sale", false)
                .putBoolean("daily_claim_visit", false).apply()
        }
        dailyCatches = prefs.getInt("daily_catches", 0)
        dailySales = prefs.getInt("daily_sales", 0)
        dailyVisited = (prefs.getStringSet("daily_visited", emptySet()) ?: emptySet()).toMutableSet()
        handler.post(frameTask)
    }
    private val frameTask = object : Runnable {
        override fun run() { invalidate(); handler.postDelayed(this, 33) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val dt = ((System.currentTimeMillis() - lastFrame) / 1000f).coerceIn(0f, .08f); lastFrame = System.currentTimeMillis()
        updateGame(dt)
        canvas.save(); canvas.scale(w / 420f, h / 820f)
        drawWorld(canvas)
        drawHud(canvas)
        when (phase) {
            Phase.SHOP -> drawShopPanel(canvas)
            Phase.TACKLE -> drawTacklePanel(canvas)
            Phase.GROUNDBAIT -> drawGroundbaitPanel(canvas)
            Phase.BAG -> drawBag(canvas)
            Phase.MAP -> drawPanel(canvas, "ВЫБОР ВОДОЁМА", locations + listOf("ТОЧКИ ЛОВЛИ", "Закрыть"))
            Phase.SPOTS -> drawPanel(canvas, "ВЫБОР ТОЧКИ ЛОВЛИ", fishingSpots + "Закрыть")
            Phase.JOURNAL -> drawJournal(canvas)
            Phase.ACHIEVEMENTS -> drawAchievements(canvas)
            Phase.QUESTS -> drawDailyQuests(canvas)
            Phase.ADMIN -> drawAdminPanel(canvas)
            Phase.TOURNAMENT -> drawTournamentPanel(canvas)
            Phase.ONLINE -> drawOnlinePanel(canvas)
            Phase.ADMIN_ONLINE -> drawOnlineAdminPanel(canvas)
            Phase.WINTER_EVENT -> drawWinterEventPanel(canvas)
            else -> Unit
        }
        canvas.restore()
    }

    private fun updateGame(dt: Float) {
        val now = System.currentTimeMillis()
        when (phase) {
            Phase.WAITING -> if (now - castAt > nextBiteDelay) { phase = Phase.BITE; biteAt = now; message = "ПОКЛЁВКА! Подсекайте!" }
            Phase.BITE -> if (now - biteAt > 2600) { phase = Phase.WAITING; castAt = now; nextBiteDelay = newBiteDelay(); message = "Рыба ушла. Ждём следующую поклёвку" }
            Phase.FIGHT -> {
                fightTime += dt
                val engine = fightEngine ?: return
                tension = engine.tension
                fishProgress = engine.progress
                when (engine.update(dt, reeling)) {
                    FishFightEngine.Result.ACTIVE -> {
                        tension = engine.tension
                        fishProgress = engine.progress
                    }
                    FishFightEngine.Result.LINE_BROKE -> {
                        phase = Phase.READY
                        reeling = false
                        fightEngine = null
                        message = "Леска не выдержала рывок рыбы! Улучшите леску или ослабьте натяжение"
                    }
                    FishFightEngine.Result.ESCAPED -> {
                        phase = Phase.READY
                        reeling = false
                        fightEngine = null
                        message = "Рыба сорвалась с крючка. Не допускайте провисания лески!"
                    }
                    FishFightEngine.Result.CAUGHT -> finishCatch()
                }
            }
            else -> Unit
        }
        bobberY = 0.4f * sin(now / 480.0).toFloat()
    }

    private fun drawWorld(c: Canvas) {
        val winter = remoteEventEnabled
        val sky = LinearGradient(0f, 0f, 0f, 340f, if (winter) intArrayOf(Color.rgb(9,24,61), Color.rgb(88,119,160)) else intArrayOf(Color.rgb(102,164,190), Color.rgb(220,207,158)), null, Shader.TileMode.CLAMP)
        p.shader = sky; c.drawRect(0f,0f,420f,360f,p); p.shader = null
        // distant forest line
        for (i in 0..34) { val x = i * 13f; val hh = 36f + (sin(i * 7.1) * 17).toFloat() + (i % 4) * 5f
            p.color = if (winter) (if (i % 2 == 0) Color.rgb(18,49,73) else Color.rgb(26,62,88)) else (if (i % 2 == 0) Color.rgb(37,78,48) else Color.rgb(49,91,52))
            c.drawCircle(x, 290f-hh*.35f, hh*.72f, p); c.drawRect(x-7, 282f-hh*.35f, x+7, 330f,p)
            if (winter) { p.color = Color.rgb(224,239,249); c.drawArc(x-hh*.55f, 290f-hh*.75f, x+hh*.55f, 290f-hh*.05f, 180f, 180f, false, p) }
        }
        if (winter) {
            p.color = Color.rgb(32, 55, 82); p.strokeWidth = 2f
            c.drawLine(0f, 298f, 420f, 298f, p)
            for (i in 0..13) { p.color = when (i % 4) { 0 -> Color.rgb(255,75,72); 1 -> Color.rgb(255,211,92); 2 -> Color.rgb(82,220,155); else -> Color.rgb(105,178,255) }; c.drawCircle(12f + i * 31f, 300f + (i % 2) * 5f, 3.2f, p) }
        }
        p.shader = LinearGradient(0f,300f,0f,610f, if (winter) intArrayOf(Color.rgb(135,183,211),Color.rgb(80,137,174),Color.rgb(40,89,128)) else intArrayOf(Color.rgb(65,132,143),Color.rgb(31,91,105),Color.rgb(20,66,78)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,300f,420f,610f,p); p.shader=null
        // water shimmer
        val now=System.currentTimeMillis()/300.0
        for (i in 0..95) { val x=(i*53f)%420f; val y=318f+(i*37f)%275f; p.color=Color.argb(25+(i%4)*9,220,236,218); p.strokeWidth=1f+(i%3); c.drawLine(x,y,x+12f+(i%5)*4f,y+sin(now+i).toFloat()*2f,p) }
        // праздничный снегопад появляется только во время активного события
        if (remoteEventEnabled) {
            val tick = System.currentTimeMillis() / 90L
            for (i in 0..54) {
                val sx = ((i * 79L + tick * (1 + i % 3)) % 440L).toFloat() - 10f
                val sy = ((i * 47L + tick * (2 + i % 2)) % 610L).toFloat()
                p.color = Color.argb(110 + (i % 4) * 28, 245, 250, 255)
                c.drawCircle(sx, sy, 1.2f + (i % 3) * .55f, p)
            }
        }
        // reeds and bank
        p.color=if(winter) Color.rgb(191,216,231) else Color.rgb(72,91,43); c.drawRect(0f,600f,420f,820f,p)
        p.color=if(winter) Color.rgb(232,243,250) else Color.rgb(113,119,61); c.drawOval(-90f,568f,510f,665f,p)
        for (i in 0..23) { val x=(i*19f)%420f; p.color=if(winter) Color.rgb(102,143,165) else if(i%2==0) Color.rgb(62,103,47) else Color.rgb(91,124,48); p.strokeWidth=3f; c.drawLine(x,620f,x-8f-(i%5),565f-(i%4)*8,p) }
        // fishing rod and line
        p.color=Color.rgb(83,47,24); p.strokeWidth=7f; p.strokeCap=Paint.Cap.ROUND
        c.drawLine(92f,640f,145f,375f,p)
        p.color=Color.rgb(220,204,158); p.strokeWidth=1.5f
        val by=if(phase==Phase.BITE) 410f else 448f+bobberY
        c.drawLine(145f,375f,235f,by,p)
        p.color=Color.rgb(242,72,48); c.drawCircle(235f,by,5f,p); c.drawRect(233f,by-9f,237f,by+2f,p)
        // status label
        p.color=Color.argb(180,12,25,23); c.drawRoundRect(12f,14f,408f,55f,9f,9f,p)
        drawText(c,"${locations[location]} • ${fishingSpots[fishingSpot]}",24f,33f,10f,Color.WHITE)
        drawText(c,"${conditions.title} • ${FishingTimeSeason.now().label}",24f,48f,9f,Color.rgb(226,236,219))
        drawText(c,"ОНЛАЙН",104f,41f,10f,Color.rgb(150,220,255)); drawText(c,"ТУРНИР",188f,41f,10f,Color.rgb(255,220,150)); drawText(c,"ЗАДАНИЯ",264f,41f,10f,Color.rgb(255,220,150)); drawText(c,"АДМИН",354f,41f,10f,Color.rgb(255,214,133))
    }

    private fun drawHud(c: Canvas) {
        p.color=Color.argb(220,17,28,25); c.drawRoundRect(12f,68f,408f,112f,9f,9f,p)
        drawText(c,"$coins монет",24f,96f,17f,Color.rgb(255,220,126)); drawText(c,"Улов: $catches",210f,96f,17f,Color.WHITE)
        val status=when(phase){Phase.READY->message;Phase.WAITING->"Поплавок на воде. Ожидаем... (${baits[selectedBait]}: ${baitStock[selectedBait]})";Phase.BITE->"ПОПЛАВОК УШЁЛ ПОД ВОДУ!";Phase.FIGHT->when(fightEngine?.behavior){FishFightEngine.Behavior.SURGING->"РЫБА ДЕЛАЕТ РЫВОК! Ослабьте леску";FishFightEngine.Behavior.TIRED->"РЫБА УСТАЛА! Можно аккуратно подматывать";else->"Вываживание: держите кнопку РЫБАЧИТЬ"};Phase.CAUGHT->"${fishName}: ${"%.2f".format(fishWeight)} кг • ${lastTrophyGrade.title}";else->message}
        p.color=Color.argb(210,15,27,24); c.drawRoundRect(14f,660f,406f,710f,8f,8f,p); drawText(c,status,24f,690f,14f,Color.WHITE)
        if(phase==Phase.FIGHT){ p.color=Color.DKGRAY;c.drawRoundRect(20f,720f,400f,739f,8f,8f,p); p.color=if(tension>.78f) Color.RED else Color.rgb(235,180,62); c.drawRoundRect(20f,720f,20f+380f*(tension/(1f+(lineLevel-1)*.10f)).coerceIn(0f,1f),739f,8f,8f,p); drawText(c,"НАТЯЖЕНИЕ ЛЕСКИ — отпустите, чтобы ослабить",22f,757f,10f,Color.WHITE); p.color=Color.rgb(76,165,107); c.drawRoundRect(20f,766f,20f+380f*fishProgress,784f,8f,8f,p) }
        val label=when(phase){Phase.READY->"ЗАБРОС";Phase.CAUGHT->"В САДОК";Phase.WAITING->"ЖДЁМ";Phase.BITE->"ПОДСЕЧЬ!";Phase.FIGHT->"ТЯНУТЬ";else->"НАЗАД"}
        button(c,270f,730f,132f,66f,label,phase==Phase.BITE || phase==Phase.FIGHT)
        button(c,14f,120f,392f,38f,if (remoteEventEnabled) "❄ НОВОГОДНИЙ ИВЕНТ • АКТИВЕН" else "❄ НОВОГОДНИЙ ИВЕНТ • ОЖИДАЕТ ЗАПУСКА",remoteEventEnabled)
        button(c,15f,730f,75f,66f,"КАРТА",false); button(c,100f,730f,75f,66f,"САДОК",false); button(c,185f,730f,75f,66f,"ЛАВКА",false)
    }
    private fun tournamentWeekKey(): Int {
        val cal = java.util.Calendar.getInstance()
        cal.firstDayOfWeek = java.util.Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        return TournamentSystem.currentWeekKey(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.WEEK_OF_YEAR))
    }

    private fun loadTournamentState(): TournamentState {
        val week = tournamentWeekKey()
        val savedWeek = prefs.getInt("tournament_week", -1)
        if (savedWeek != week) {
            val fresh = TournamentSystem.fresh(week)
            persistTournamentState(fresh)
            return fresh
        }
        val grade = TrophyGrade.values().firstOrNull { it.code == prefs.getString("tournament_best_grade", "none") } ?: TrophyGrade.NONE
        return TournamentState(
            week, prefs.getBoolean("tournament_joined", false),
            prefs.getInt("tournament_catches", 0),
            prefs.getFloat("tournament_total_weight", 0f).toDouble(),
            prefs.getString("tournament_best_name", "—") ?: "—",
            prefs.getFloat("tournament_best_weight", 0f).toDouble(), grade
        )
    }

    private fun persistTournamentState(state: TournamentState) {
        prefs.edit().putInt("tournament_week", state.weekKey)
            .putBoolean("tournament_joined", state.joined)
            .putInt("tournament_catches", state.catches)
            .putFloat("tournament_total_weight", state.totalWeightKg.toFloat())
            .putString("tournament_best_name", state.bestFishName)
            .putFloat("tournament_best_weight", state.bestWeightKg.toFloat())
            .putString("tournament_best_grade", state.bestGrade.code).apply()
    }

    private fun saveTournamentState() = persistTournamentState(tournamentState)

    private fun drawTournamentPanel(c: Canvas) {
        p.color = Color.argb(245, 8, 19, 18)
        c.drawRoundRect(15f, 135f, 405f, 680f, 15f, 15f, p)
        drawText(c, "ТУРНИР НЕДЕЛИ", 32f, 174f, 20f, Color.rgb(255, 214, 133))
        drawText(c, tournamentKind.title, 32f, 220f, 17f, Color.WHITE)
        drawText(c, tournamentKind.description, 32f, 248f, 11f, Color.LTGRAY)
        drawText(c, "Участие: ${if (tournamentState.joined) "ПРИНЯТО" else "НЕ НАЧАТО"}", 32f, 295f, 14f, if (tournamentState.joined) Color.rgb(120, 220, 145) else Color.LTGRAY)
        val standing = TournamentSystem.standing(tournamentState, tournamentKind)
        drawText(c, standing.title, 32f, 345f, 13f, Color.rgb(255, 220, 150))
        drawText(c, "${"%.2f".format(java.util.Locale.US, standing.score)} кг", 32f, 390f, 27f, Color.WHITE)
        drawText(c, standing.detail, 32f, 420f, 12f, Color.LTGRAY)
        if (remoteTournamentEnabled) {
            drawText(c, "СЕРВЕР: $onlineTournamentStatus".take(55), 32f, 448f, 10f, Color.rgb(150, 220, 170))
            drawText(c, "РЕЙТИНГ ОНЛАЙН", 32f, 470f, 10f, Color.rgb(255, 220, 150))
            onlineTournamentBoard.take(2).forEachIndexed { i, line -> drawText(c, line.take(48), 32f, 488f + i * 14f, 9f, Color.LTGRAY) }
            drawText(c, "ОНЛАЙН: ${remoteTournamentTitle.ifBlank { "Турнир клуба" }.take(34)}", 32f, 650f, 10f, Color.rgb(120, 220, 145))
            drawText(c, remoteTournamentMessage.take(52), 32f, 668f, 9f, Color.LTGRAY)
        } else {
            drawText(c, "Поймано в турнире: ${tournamentState.catches}", 32f, 455f, 12f, Color.WHITE)
            drawText(c, "Лучший трофей: ${tournamentState.bestGrade.title}", 32f, 480f, 11f, Color.LTGRAY)
            drawText(c, "Локальный режим: онлайн-соревнование не запущено", 32f, 660f, 9f, Color.rgb(235, 180, 120))
        }
        val canJoinTournament = if (remoteTournamentEnabled) !onlineTournamentJoined else !tournamentState.joined
        button(c, 30f, 510f, 360f, 52f, if (!canJoinTournament) "ТЫ УЧАСТВУЕШЬ" else "ПРИНЯТЬ УЧАСТИЕ", !canJoinTournament)
        button(c, 30f, 580f, 360f, 48f, "СМЕНИТЬ ТИП ТУРНИРА", false)
    }

    private fun drawWinterEventPanel(c: Canvas) {
        p.color = Color.argb(248, 7, 20, 38)
        c.drawRoundRect(12f, 118f, 408f, 700f, 18f, 18f, p)
        drawText(c, "НОВОГОДНИЙ УЛОВ", 28f, 151f, 21f, Color.rgb(255, 221, 151))
        val banner = winterEventBanner
        if (banner != null) {
            val src = Rect(0, 0, banner.width, banner.height)
            val dst = RectF(26f, 166f, 394f, 326f)
            c.save()
            val path = Path().apply { addRoundRect(dst, 12f, 12f, Path.Direction.CW) }
            c.clipPath(path)
            p.color = Color.WHITE
            c.drawBitmap(banner, src, dst, p)
            c.restore()
        }
        if (!remoteEventEnabled) {
            drawText(c, "Праздник пока не начался", 30f, 365f, 16f, Color.WHITE)
            drawText(c, "Администратор сможет включить его в любой момент.", 30f, 393f, 11f, Color.LTGRAY)
            drawText(c, "Когда ивент активен, здесь появятся особые задания", 30f, 416f, 10f, Color.LTGRAY)
            drawText(c, "и праздничные награды.", 30f, 435f, 10f, Color.LTGRAY)
        } else {
            drawText(c, remoteEventTitle.ifBlank { "Полярная звезда" }.take(42), 30f, 355f, 15f, Color.rgb(255, 221, 151))
            drawText(c, remoteEventMessage.ifBlank { "Три испытания зимнего рыбака. Забирай награды!" }.take(54), 30f, 378f, 9f, Color.LTGRAY)
            val names = listOf("ЛЕДЯНОЙ МАРШРУТ", "ХИЩНИК ПОЛЯРНОЙ НОЧИ", "ЗОЛОТОЙ ТРОФЕЙ")
            val desc = listOf("Поймай рыбу на 3 разных водоёмах", "Поймай щуку весом от 4 кг", "Поймай любую трофейную рыбу")
            val progress = listOf("${winterCatchLocations.size.coerceAtMost(3)}/3 водоёма", "${winterPikeCount.coerceAtMost(1)}/1 щука", "${winterTrophyCount.coerceAtMost(1)}/1 трофей")
            val rewards = listOf(120, 180, 220)
            for (i in 0..2) {
                val y = 397f + i * 76f
                p.color = Color.rgb(22, 49, 69)
                c.drawRoundRect(25f, y, 395f, y + 66f, 9f, 9f, p)
                drawText(c, names[i], 37f, y + 19f, 11f, Color.rgb(255, 222, 157))
                drawText(c, desc[i], 37f, y + 36f, 9f, Color.WHITE)
                drawText(c, "${progress[i]} • +${rewards[i]} монет", 37f, y + 53f, 9f, Color.rgb(151, 225, 190))
                button(c, 305f, y + 12f, 78f, 42f, if (winterClaimed[i]) "ВЗЯТО" else if (winterGoalDone(i)) "ЗАБРАТЬ" else "В РАБОТЕ", winterGoalDone(i) && !winterClaimed[i])
            }
            if ((0..2).all { winterClaimed[it] }) drawText(c, "ПРАЗДНИЧНЫЙ НАБОР СОБРАН! Ты — легенда зимы!", 30f, 645f, 10f, Color.rgb(255, 221, 151))
        }
        button(c, 28f, 656f, 364f, 34f, "НАЗАД К РЫБАЛКЕ", false)
    }

    private fun winterGoalDone(index: Int): Boolean = when (index) {
        0 -> winterCatchLocations.size >= 3
        1 -> winterPikeCount >= 1
        2 -> winterTrophyCount >= 1
        else -> false
    }

    private fun resetWinterEventProgressIfNeeded() {
        if (!remoteEventEnabled) return
        val key = remoteEventTitle.ifBlank { "НОВОГОДНИЙ УЛОВ" } + "|" + winterEventUpdated
        if (key != winterEventKey) {
            winterEventKey = key
            winterCatchLocations.clear(); winterPikeCount = 0; winterTrophyCount = 0
            winterClaimed = mutableListOf(false, false, false)
            prefs.edit().putString("winter_event_key", key).putStringSet("winter_catch_locations", emptySet())
                .putString("winter_event_updated", winterEventUpdated)
                .putInt("winter_pike_count", 0).putInt("winter_trophy_count", 0)
                .putBoolean("winter_claim_0", false).putBoolean("winter_claim_1", false).putBoolean("winter_claim_2", false).apply()
        }
    }

    private fun recordWinterEventCatch() {
        if (!remoteEventEnabled) return
        winterCatchLocations.add(location.toString())
        if (fishName.contains("Щука", ignoreCase = true) && fishWeight >= 4.0) winterPikeCount++
        if (lastTrophyGrade != TrophyGrade.NONE) winterTrophyCount++
        prefs.edit().putStringSet("winter_catch_locations", winterCatchLocations)
            .putInt("winter_pike_count", winterPikeCount).putInt("winter_trophy_count", winterTrophyCount).apply()
    }

    private fun claimWinterReward(index: Int) {
        if (!remoteEventEnabled || index !in 0..2 || winterClaimed[index] || !winterGoalDone(index)) return
        val reward = listOf(120, 180, 220)[index]
        coins += reward
        winterClaimed[index] = true
        prefs.edit().putBoolean("winter_claim_$index", true).apply()
        message = "Праздничная награда: +$reward монет"
        save()
    }

    private fun drawOnlinePanel(c: Canvas) {
        p.color = Color.argb(246, 8, 19, 18)
        c.drawRoundRect(14f, 125f, 406f, 690f, 15f, 15f, p)
        drawText(c, "ОНЛАЙН-КЛУБ", 30f, 164f, 21f, Color.rgb(255, 214, 133))
        drawText(c, onlineStatus.take(48), 30f, 194f, 12f, Color.rgb(132, 220, 155))
        drawText(c, onlineProfile.take(48), 30f, 220f, 12f, Color.WHITE)
        val liveLabel = when {
            remoteEventEnabled && remoteTournamentEnabled -> "ИВЕНТ И ТУРНИР АКТИВНЫ • НОВОСТИ"
            remoteEventEnabled -> "АКТИВЕН ИВЕНТ • НОВОСТИ"
            remoteTournamentEnabled -> "АКТИВЕН ОНЛАЙН-ТУРНИР • НОВОСТИ"
            else -> "НОВОСТИ И СОБЫТИЯ КЛУБА"
        }
        button(c, 28f, 232f, 364f, 42f, liveLabel, remoteEventEnabled || remoteTournamentEnabled)
        button(c, 28f, 282f, 364f, 42f, "ВОЙТИ / РЕГИСТРАЦИЯ", true)
        button(c, 28f, 332f, 364f, 40f, "ОБНОВИТЬ ОБЩИЙ РЕЙТИНГ", false)
        drawText(c, "ТОП РЫБАКОВ", 30f, 402f, 16f, Color.rgb(255, 214, 133))
        if (onlineLeaderboard.isEmpty()) {
            drawText(c, "Пока нет результатов или рейтинг не загружен", 30f, 434f, 10f, Color.LTGRAY)
        } else {
            onlineLeaderboard.take(7).forEachIndexed { index, line ->
                drawText(c, line.take(54), 32f, 434f + index * 25f, 11f, Color.WHITE)
            }
        }
        button(c, 28f, 635f, 174f, 42f, "АДРЕС СЕРВЕРА", false)
        button(c, 214f, 635f, 178f, 42f, "ВЫЙТИ ИЗ АККАУНТА", false)
        if (onlineIsAdmin) button(c, 28f, 555f, 364f, 42f, "ПАНЕЛЬ АДМИНИСТРАТОРА", true)
        drawText(c, "Нужен ваш HTTPS-адрес размещённого сервера", 30f, 610f, 9f, Color.LTGRAY)
    }

    private fun drawOnlineAdminPanel(c: Canvas) {
        p.color = Color.argb(246, 8, 19, 18)
        c.drawRoundRect(14f, 125f, 406f, 690f, 15f, 15f, p)
        drawText(c, "АДМИНИСТРИРОВАНИЕ", 28f, 164f, 19f, Color.rgb(255, 214, 133))
        drawText(c, adminOnlineStatus.take(52), 28f, 190f, 10f, Color.LTGRAY)
        val rows = listOf("СПИСОК ИГРОКОВ / БАНЫ / НАГРАДЫ", "НОВОГОДНИЙ ИВЕНТ: ВКЛ / ВЫКЛ", "ВКЛЮЧИТЬ / ВЫКЛЮЧИТЬ ТУРНИР", "ОПУБЛИКОВАТЬ ОБЪЯВЛЕНИЕ", "ЖУРНАЛ ДЕЙСТВИЙ", "НАЗАД В ОНЛАЙН")
        rows.forEachIndexed { i, label -> button(c, 26f, 215f + i * 68f, 368f, 52f, label, i == 0) }
        drawText(c, "Все команды проверяются сервером по роли аккаунта", 28f, 650f, 9f, Color.LTGRAY)
    }

    private fun showOnlineBulletin() {
        val lines = mutableListOf<String>()
        if (remoteEventEnabled) lines += "ИВЕНТ: ${remoteEventTitle.ifBlank { "Рыболовный ивент" }}\n${remoteEventMessage}"
        if (remoteTournamentEnabled) lines += "ТУРНИР: ${remoteTournamentTitle.ifBlank { "Соревнование рыбаков" }}\n${remoteTournamentMessage}"
        lines += onlineAnnouncements
        val body = if (lines.isEmpty()) "Сейчас нет активных событий и новых объявлений. Заглядывайте позже!" else lines.joinToString("\n\n────────────\n\n")
        AlertDialog.Builder(context).setTitle("События и новости клуба").setMessage(body)
            .setPositiveButton("Обновить") { _, _ -> refreshOnlineData() }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun openOnlineAdmin() {
        val token = sessionStore.load()
        val client = api()
        if (token == null || client == null || !onlineIsAdmin) {
            adminOnlineStatus = "Нет прав администратора или активной сессии"
            invalidate(); return
        }
        phase = Phase.ADMIN_ONLINE
        adminOnlineStatus = "Загружаем данные…"
        invalidate()
    }

    private fun loadAdminPlayers() {
        val token = sessionStore.load() ?: return
        val client = api() ?: return
        adminOnlineStatus = "Загружаем список игроков…"; invalidate()
        client.adminUsers(token) { result ->
            if (!result.ok) { adminOnlineStatus = result.error ?: "Ошибка загрузки игроков"; invalidate(); return@adminUsers }
            val items = result.body?.optJSONArray("items")
            if (items == null || items.length() == 0) { adminOnlineStatus = "Игроков пока нет"; invalidate(); return@adminUsers }
            val users = (0 until items.length()).mapNotNull { i -> items.optJSONObject(i)?.let { u ->
                Triple(u.optString("id"), "${u.optString("displayName")} (@${u.optString("username")}) • ${u.optLong("coins")} монет${if (u.optBoolean("banned")) " • БАН" else ""}", u.optBoolean("banned"))
            } }
            AlertDialog.Builder(context).setTitle("Игроки (${users.size})")
                .setItems(users.map { it.second }.toTypedArray()) { _, index -> showAdminPlayerActions(users[index].first, users[index].second, users[index].third) }
                .setPositiveButton("Обновить") { _, _ -> loadAdminPlayers() }.setNegativeButton("Закрыть", null).show()
            adminOnlineStatus = "Загружено игроков: ${users.size}"; invalidate()
        }
    }

    private fun showAdminPlayerActions(userId: String, label: String, banned: Boolean) {
        val token = sessionStore.load() ?: return
        val client = api() ?: return
        val actions = arrayOf(if (banned) "Снять бан" else "Заблокировать", "Выдать монеты")
        AlertDialog.Builder(context).setTitle(label).setItems(actions) { _, which ->
            if (which == 0) client.setBan(token, userId, !banned) { r ->
                adminOnlineStatus = if (r.ok) if (banned) "Бан снят" else "Игрок заблокирован" else (r.error ?: "Ошибка операции")
                invalidate()
            } else {
                val amount = EditText(context).apply { hint = "Количество (1–100000)"; inputType = InputType.TYPE_CLASS_NUMBER }
                AlertDialog.Builder(context).setTitle("Выдать игровую валюту").setView(amount)
                    .setNegativeButton("Отмена", null).setPositiveButton("Выдать") { _, _ ->
                        val value = amount.text.toString().toIntOrNull() ?: 0
                        if (value !in 1..100000) { adminOnlineStatus = "Допустимо от 1 до 100000 монет"; invalidate() }
                        else client.grantCoins(token, userId, value) { r -> adminOnlineStatus = if (r.ok) "Выдано $value монет" else (r.error ?: "Ошибка выдачи"); invalidate() }
                    }.show()
            }
        }.show()
    }

    private fun toggleAdminFeature(kind: String) {
        val token = sessionStore.load() ?: return
        val client = api() ?: return
        client.features { r ->
            if (!r.ok) { adminOnlineStatus = r.error ?: "Не удалось получить настройки"; invalidate(); return@features }
            val key = if (kind == "events") "events" else "tournaments"
            val current = r.body?.optJSONObject(key)
            val enabled = !(current?.optBoolean("enabled") ?: false)
            val title = if (kind == "events") {
                current?.optString("title")?.takeIf { it.isNotBlank() && it != "Рыболовный ивент" } ?: "НОВОГОДНИЙ УЛОВ: ПОЛЯРНАЯ ЗВЕЗДА"
            } else current?.optString("title")?.takeIf { it.isNotBlank() } ?: "Соревнование рыбаков"
            val message = if (kind == "events") {
                current?.optString("message")?.takeIf { it.isNotBlank() && it != "Участвуйте и ловите рыбу!" } ?: "Три зимних испытания: облови 3 водоёма, поймай щуку от 4 кг и добудь трофей. Награды ждут!"
            } else current?.optString("message") ?: "Участвуйте и ловите рыбу!"
            val done: (ru.reborn.fishing.online.OnlineApi.Result) -> Unit = { result ->
                adminOnlineStatus = if (result.ok) "${if (kind == "events") "Ивент" else "Турнир"} ${if (enabled) "включён" else "выключен"}" else (result.error ?: "Ошибка настройки")
                invalidate()
            }
            if (kind == "events") client.setEvents(token, enabled, title, message, done) else client.setTournaments(token, enabled, title, message, done)
        }
    }

    private fun composeAdminAnnouncement() {
        val token = sessionStore.load() ?: return
        val client = api() ?: return
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 8, 36, 0) }
        val title = EditText(context).apply { hint = "Заголовок объявления" }
        val message = EditText(context).apply { hint = "Текст объявления"; minLines = 3 }
        box.addView(title); box.addView(message)
        AlertDialog.Builder(context).setTitle("Новое объявление").setView(box).setNegativeButton("Отмена", null)
            .setPositiveButton("Опубликовать") { _, _ -> client.announce(token, title.text.toString(), message.text.toString()) { r ->
                adminOnlineStatus = if (r.ok) "Объявление опубликовано" else (r.error ?: "Ошибка публикации"); invalidate()
            } }.show()
    }

    private fun showAdminAudit() {
        val token = sessionStore.load() ?: return
        val client = api() ?: return
        client.adminAudit(token) { r ->
            val items = r.body?.optJSONArray("items")
            val lines = if (!r.ok) listOf(r.error ?: "Ошибка загрузки") else if (items == null || items.length() == 0) listOf("Записей пока нет") else (0 until items.length()).map { i ->
                val a = items.optJSONObject(i)
                "${a?.optString("action")} • ${a?.optString("details")} • ${a?.optString("createdAt")}"
            }
            AlertDialog.Builder(context).setTitle("Журнал действий").setItems(lines.toTypedArray(), null).setPositiveButton("Закрыть", null).show()
        }
    }

    private fun api(): ru.reborn.fishing.online.OnlineApi? {
        val endpoint = prefs.getString("online_endpoint", "")?.trim().orEmpty()
        if (!endpoint.startsWith("https://")) return null
        if (onlineApi == null) onlineApi = try { ru.reborn.fishing.online.OnlineApi(endpoint) } catch (_: Exception) { null }
        return onlineApi
    }

    private fun showEndpointDialog(afterSave: (() -> Unit)? = null) {
        val field = EditText(context).apply {
            hint = "https://ваш-домен.example"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("online_endpoint", ""))
        }
        AlertDialog.Builder(context).setTitle("Адрес игрового сервера")
            .setMessage("Укажите HTTPS-адрес сервера Русской Рыбалки: Reborn")
            .setView(field)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val value = field.text.toString().trim().trimEnd('/')
                if (!value.startsWith("https://")) {
                    onlineStatus = "Адрес должен начинаться с https://"
                } else {
                    onlineApi?.close(); onlineApi = null
                    prefs.edit().putString("online_endpoint", value).apply()
                    onlineStatus = "Адрес сервера сохранён"
                    afterSave?.invoke()
                }
                invalidate()
            }.show()
    }

    private fun showAccountDialog() {
        val choices = arrayOf("Войти", "Создать аккаунт")
        AlertDialog.Builder(context).setTitle("Игровой аккаунт")
            .setItems(choices) { _, which -> showCredentialsDialog(which == 1) }.setNegativeButton("Отмена", null).show()
    }

    private fun showCredentialsDialog(register: Boolean) {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(42, 8, 42, 0) }
        fun field(hintText: String, type: Int): EditText = EditText(context).also {
            it.hint = hintText; it.inputType = type; it.setSingleLine(true)
            box.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val username = field("Логин (латиница, 3–20 символов)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        val display = if (register) field("Имя в игре", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME) else null
        val password = field("Пароль (не менее 10 символов)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        AlertDialog.Builder(context).setTitle(if (register) "Регистрация" else "Вход")
            .setView(box).setNegativeButton("Отмена", null)
            .setPositiveButton(if (register) "Создать" else "Войти") { _, _ ->
                val client = api()
                if (client == null) { onlineStatus = "Сначала укажите HTTPS-адрес сервера"; showEndpointDialog { showCredentialsDialog(register) }; invalidate(); return@setPositiveButton }
                onlineBusy = true; onlineStatus = "Подключаемся…"; invalidate()
                val done: (ru.reborn.fishing.online.OnlineApi.Result) -> Unit = { result ->
                    onlineBusy = false
                    if (result.ok) {
                        val token = result.body?.optString("token").orEmpty()
                        if (token.isNotBlank()) {
                            sessionStore.save(token)
                            val user = result.body?.optJSONObject("user")
                            onlineProfile = user?.optString("displayName", username.text.toString()) ?: username.text.toString()
                            onlineIsAdmin = user?.optString("role") == "admin"
                            onlineStatus = if (onlineIsAdmin) "Аккаунт администратора подключён" else "Аккаунт подключён"
                            refreshOnlineData()
                        } else onlineStatus = "Сервер не вернул токен"
                    } else onlineStatus = result.error ?: "Ошибка сервера (${result.status})"
                    invalidate()
                }
                if (register) client.register(username.text.toString().trim(), display?.text?.toString()?.trim().orEmpty(), password.text.toString(), done)
                else client.login(username.text.toString().trim(), password.text.toString(), done)
            }.show()
    }

    private fun refreshOnlineData() {
        val client = api() ?: return
        client.features { result ->
            if (result.ok) {
                val events = result.body?.optJSONObject("events")
                val tournaments = result.body?.optJSONObject("tournaments")
                remoteEventEnabled = events?.optBoolean("enabled") ?: false
                remoteEventTitle = events?.optString("title").orEmpty()
                remoteEventMessage = events?.optString("message").orEmpty()
                winterEventUpdated = events?.optString("updatedAt").orEmpty()
                resetWinterEventProgressIfNeeded()
                remoteTournamentEnabled = tournaments?.optBoolean("enabled") ?: false
                remoteTournamentTitle = tournaments?.optString("title").orEmpty()
                remoteTournamentMessage = tournaments?.optString("message").orEmpty()
            }
            invalidate()
        }
        client.announcements { result ->
            if (result.ok) {
                val items = result.body?.optJSONArray("items")
                onlineAnnouncements = if (items == null) emptyList() else (0 until items.length()).mapNotNull { i ->
                    items.optJSONObject(i)?.let { "${it.optString("title")}\n${it.optString("message")}" }
                }
            }
            invalidate()
        }
        val token = sessionStore.load()
        if (token != null) {
            flushPendingCatches()
            client.me(token) { result ->
            if (result.ok) {
                onlineProfile = result.body?.optString("displayName", "Рыбак") ?: "Рыбак"
                onlineIsAdmin = result.body?.optString("role") == "admin"
                onlineStatus = if (onlineIsAdmin) "Вы вошли как администратор" else "Вы вошли в игру"
            } else { sessionStore.clear(); onlineIsAdmin = false; onlineProfile = "Войдите или создайте аккаунт"; onlineStatus = "Сессия завершилась. Войдите снова" }
            invalidate()
            }
        }
        if (token != null) client.tournamentLeaderboard(token) { result ->
            if (result.ok) {
                onlineTournamentJoined = result.body?.optBoolean("joined") ?: false
                onlineTournamentStatus = if (onlineTournamentJoined) "участие подтверждено" else if (result.body?.optBoolean("enabled") == true) "можно вступить" else "турнир не запущен"
                val rows = result.body?.optJSONArray("items")
                onlineTournamentBoard = if (rows == null) emptyList() else (0 until rows.length()).map { i ->
                    val row = rows.optJSONObject(i)
                    "${i + 1}. ${row?.optString("displayName").orEmpty()} — ${"%.2f".format(java.util.Locale.US, row?.optDouble("bestWeightKg") ?: 0.0)} кг"
                }
            } else if (result.status == 401) onlineTournamentStatus = "требуется вход"
            invalidate()
        }
        client.leaderboard { result ->
            if (result.ok) {
                val items = result.body?.optJSONArray("items")
                onlineLeaderboard = if (items == null) emptyList() else (0 until items.length()).map { i ->
                    val item = items.optJSONObject(i)
                    "${item?.optInt("rank", i + 1)}. ${item?.optString("displayName", "Рыбак")} — ${String.format(java.util.Locale.US, "%.2f", item?.optDouble("bestWeightKg", 0.0) ?: 0.0)} кг"
                }
                onlineStatus = "Общий рейтинг обновлён"
            } else onlineStatus = result.error ?: "Не удалось загрузить рейтинг"
            invalidate()
        }
    }

    private fun submitOnlineCatch() {
        val eventId = java.util.UUID.randomUUID().toString()
        val fishId = fishName.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-zа-я0-9]+"), "_")
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val record = org.json.JSONObject().put("eventId", eventId).put("fishId", fishId)
            .put("weightKg", fishWeight).put("locationId", "location_$location").put("caughtAt", stamp)
        val pending = (prefs.getStringSet("online_pending_catches", emptySet()) ?: emptySet()).toMutableSet()
        pending.add(record.toString())
        prefs.edit().putStringSet("online_pending_catches", pending).apply()
        flushPendingCatches()
    }

    /** Offline catches stay queued; the server's idempotency key makes retries safe. */
    private fun flushPendingCatches() {
        val token = sessionStore.load() ?: return
        val client = api() ?: return
        val pending = (prefs.getStringSet("online_pending_catches", emptySet()) ?: emptySet()).toList()
        pending.forEach { raw ->
            val item = try { org.json.JSONObject(raw) } catch (_: Exception) { null } ?: return@forEach
            val eventId = item.optString("eventId")
            client.submitCatch(token, eventId, item.optString("fishId"), item.optDouble("weightKg"),
                item.optString("locationId"), item.optString("caughtAt")) { result ->
                if (result.ok) {
                    val remaining = (prefs.getStringSet("online_pending_catches", emptySet()) ?: emptySet()).toMutableSet()
                    remaining.remove(raw)
                    prefs.edit().putStringSet("online_pending_catches", remaining).apply()
                    onlineStatus = "Улов отправлен в общий рейтинг"
                } else onlineStatus = "Улов ожидает отправки: ${result.error ?: "нет связи"}"
                invalidate()
            }
        }
    }

    private fun drawJournal(c: Canvas) {
        p.color = Color.argb(245, 8, 19, 18)
        c.drawRoundRect(12f, 120f, 408f, 650f, 14f, 14f, p)
        drawText(c, "РЫБОЛОВНЫЙ ЖУРНАЛ", 28f, 153f, 19f, Color.rgb(255, 214, 133))
        drawText(c, "Вид • поймано • лучший вес", 28f, 180f, 12f, Color.LTGRAY)
        FishingCatalog.fish.forEachIndexed { i, fish ->
            val y = 190f + i * 29f
            val count = prefs.getInt("fish_count_$i", 0)
            val best = prefs.getFloat("fish_best_$i", 0f)
            val trophies = prefs.getInt("fish_trophies_$i", 0)
            p.color = if (count > 0) Color.rgb(39, 73, 53) else Color.rgb(37, 48, 46)
            c.drawRoundRect(24f, y, 396f, y + 25f, 5f, 5f, p)
            val record = if (count > 0) String.format(java.util.Locale.US, "%.2f кг", best) else "—"
            drawText(c, if (count > 0) fish.name else "Не поймана: ${fish.name}", 32f, y + 17f, 10f, if (count > 0) Color.WHITE else Color.LTGRAY)
            drawText(c, "$count • $record • ★$trophies", 270f, y + 17f, 9f, Color.rgb(255, 220, 150))
        }
        button(c, 145f, 665f, 130f, 48f, "НАЗАД", false)
    }

    private fun drawShopPanel(c: Canvas) {
        p.color = Color.argb(235, 9, 19, 17)
        c.drawRoundRect(25f, 165f, 395f, 700f, 18f, 18f, p)
        drawText(c, "ЛАВКА И МАСТЕРСКАЯ", 45f, 220f, 20f, Color.rgb(255, 214, 133))
        val rows = listOf(
            "Червь +10 — бесплатно", "Опарыш +10 — 8 монет", "Кукуруза +10 — 12 монет", "Перловка +10 — 10 монет",
            "Удилище ур. $rodLevel — ${gearCost(rodLevel)} монет", "Катушка ур. $reelLevel — ${gearCost(reelLevel)} монет",
            "Леска ур. $lineLevel — ${gearCost(lineLevel)} монет", "ПРИКОРМКА И СМЕСИ", "СОБРАТЬ СНАСТЬ"
        )
        rows.forEachIndexed { i, row ->
            val y = 230f + i * 49f
            p.color = if (i == 7) Color.rgb(105, 67, 35) else Color.rgb(45, 65, 50)
            c.drawRoundRect(42f, y, 378f, y + 43f, 7f, 7f, p)
            drawText(c, row, 55f, y + 27f, 13f, Color.WHITE)
        }
    }

    private fun drawTacklePanel(c: Canvas) {
        p.color = Color.argb(245, 8, 19, 18)
        c.drawRoundRect(15f, 145f, 405f, 680f, 15f, 15f, p)
        drawText(c, "КОНСТРУКТОР СНАСТИ", 32f, 182f, 20f, Color.rgb(255, 214, 133))
        drawText(c, "Выбирай компоненты своей удочки", 32f, 207f, 11f, Color.LTGRAY)
        TacklePart.values().forEachIndexed { i, part ->
            val y = 225f + i * 91f
            val item = tackle.option(part)
            val next = TackleSystem.options.getValue(part).getOrNull(tackle.index(part) + 1)
            p.color = Color.rgb(42, 65, 51)
            c.drawRoundRect(28f, y, 392f, y + 78f, 8f, 8f, p)
            drawText(c, "${part.title}: ${item.name}", 42f, y + 24f, 13f, Color.WHITE)
            drawText(c, item.description, 42f, y + 44f, 10f, Color.LTGRAY)
            drawText(c, next?.let { "УЛУЧШИТЬ • ${it.price} мон." } ?: "МАКСИМАЛЬНЫЙ УРОВЕНЬ", 42f, y + 65f, 10f, Color.rgb(255, 220, 150))
        }
        drawText(c, "Монеты: $coins", 32f, 605f, 13f, Color.rgb(255, 220, 126))
        button(c, 145f, 620f, 130f, 46f, "ГОТОВО", false)
    }

    private fun drawGroundbaitPanel(c: Canvas) {
        p.color = Color.argb(245, 8, 19, 18)
        c.drawRoundRect(15f, 145f, 405f, 680f, 15f, 15f, p)
        drawText(c, "ПРИКОРМКА", 32f, 182f, 20f, Color.rgb(255, 214, 133))
        drawText(c, "Одна порция действует на следующий заброс", 32f, 207f, 10f, Color.LTGRAY)
        GroundbaitMix.values().forEachIndexed { i, mix ->
            val y = 225f + i * 112f
            p.color = Color.rgb(42, 65, 51)
            c.drawRoundRect(28f, y, 392f, y + 98f, 8f, 8f, p)
            drawText(c, mix.title, 42f, y + 24f, 14f, Color.WHITE)
            drawText(c, mix.description, 42f, y + 46f, 10f, Color.LTGRAY)
            drawText(c, "В запасе: ${groundbaitStock[i]} порц. • пачка ${mix.pricePerPack} мон.", 42f, y + 68f, 11f, Color.rgb(255, 220, 150))
            drawText(c, if (selectedGroundbait == i) "ВЫБРАНА • нажмите ниже, чтобы купить" else "ВЫБРАТЬ • нажмите ниже, чтобы купить", 42f, y + 87f, 10f, Color.WHITE)
        }
        drawText(c, "Монеты: $coins", 32f, 590f, 13f, Color.rgb(255, 220, 126))
        button(c, 145f, 620f, 130f, 46f, "НАЗАД", false)
    }

    private fun drawAdminPanel(c: Canvas) {
        p.color = Color.argb(245, 7, 17, 18); c.drawRoundRect(15f, 105f, 405f, 650f, 16f, 16f, p)
        drawText(c, "ПАНЕЛЬ АДМИНИСТРАТОРА", 32f, 140f, 19f, Color.rgb(255, 214, 133))
        drawText(c, "Ивент: ${if (admin.eventEnabled) "ВКЛ" else "ВЫКЛ"}   •   Турнир: ${if (admin.tournamentEnabled) "ВКЛ" else "ВЫКЛ"}", 30f, 172f, 13f, Color.WHITE)
        val rows = admin.players()
        val labels = listOf("1. ${if (admin.eventEnabled) "Выключить" else "Включить"} сезонный ивент", "2. ${if (admin.tournamentEnabled) "Остановить" else "Запустить"} турнир") + rows.mapIndexed { i, player ->
            "${i + 3}. ${player.nickname} • ${if (player.banned) "БАН" else "активен"} • ${player.coins} монет"
        } + listOf("6. Выдать 500 монет выбранному", "7. Объявление: изменить (демо)", "8. Журнал действий", "9. Закрыть админку")
        labels.forEachIndexed { i, label ->
            val y = 188f + i * 43f
            p.color = if (i < 2) Color.rgb(69, 79, 42) else Color.rgb(37, 58, 52)
            c.drawRoundRect(28f, y, 392f, y + 36f, 7f, 7f, p)
            drawText(c, label, 38f, y + 24f, 12f, Color.WHITE)
        }
        drawText(c, adminMessage.take(58), 30f, 612f, 10f, Color.rgb(255, 220, 150))
        drawText(c, "Демо-данные локальны. Реальный бан/выдача требуют серверной авторизации.", 25f, 632f, 9f, Color.LTGRAY)
    }

    private fun drawPanel(c:Canvas,title:String,rows:List<String>){ p.color=Color.argb(235,9,19,17);c.drawRoundRect(25f,180f,395f,635f,18f,18f,p);drawText(c,title,45f,220f,20f,Color.rgb(255,214,133)); rows.forEachIndexed{ i,s->p.color=Color.rgb(45,65,50);c.drawRoundRect(42f,242f+i*54,378f,288f+i*54,7f,7f,p);drawText(c,s,55f,272f+i*54,14f,Color.WHITE)} }
    private fun button(c:Canvas,x:Float,y:Float,w:Float,h:Float,label:String,hot:Boolean){p.color=if(hot)Color.rgb(181,72,29) else Color.rgb(51,82,61);c.drawRoundRect(x,y,x+w,y+h,10f,10f,p);p.color=Color.rgb(223,195,137);p.style=Paint.Style.STROKE;p.strokeWidth=2f;c.drawRoundRect(x,y,x+w,y+h,10f,10f,p);p.style=Paint.Style.FILL;drawText(c,label,x+10f,y+h/2+5f,if(w<80)11f else 14f,Color.WHITE)}
    private fun drawText(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int){text.textSize=size;text.color=color;text.setShadowLayer(2f,1f,1f,Color.BLACK);c.drawText(s,x,y,text);text.clearShadowLayer()}

    override fun onTouchEvent(e:MotionEvent):Boolean {
        val x=e.x/(width/420f);val y=e.y/(height/820f)
        when(e.action){
            MotionEvent.ACTION_DOWN->{touchDownAt=System.currentTimeMillis(); if(phase==Phase.FIGHT && x>265 && y>720){reeling=true}; return true}
            MotionEvent.ACTION_UP->{
                reeling=false
                if (phase == Phase.WINTER_EVENT) {
                    if (remoteEventEnabled && y in 397f..625f) {
                        val idx = ((y - 397f) / 76f).toInt()
                        if (idx in 0..2 && x >= 295f) claimWinterReward(idx)
                    }
                    phase = Phase.READY
                    invalidate(); return true
                }
                if (phase == Phase.ADMIN_ONLINE) {
                    when {
                        y in 215f..267f -> loadAdminPlayers()
                        y in 283f..335f -> toggleAdminFeature("events")
                        y in 351f..403f -> toggleAdminFeature("tournaments")
                        y in 419f..471f -> composeAdminAnnouncement()
                        y in 487f..539f -> showAdminAudit()
                        y in 555f..610f -> phase = Phase.ONLINE
                        else -> phase = Phase.ONLINE
                    }
                    invalidate(); return true
                }
                if (phase == Phase.ONLINE) {
                    when {
                        y in 555f..610f && onlineIsAdmin -> openOnlineAdmin()
                        y in 225f..278f -> showOnlineBulletin()
                        y in 278f..328f -> showAccountDialog()
                        y in 328f..380f -> refreshOnlineData()
                        y in 625f..685f && x < 210f -> showEndpointDialog { refreshOnlineData() }
                        y in 625f..685f && x >= 210f -> { sessionStore.clear(); onlineIsAdmin = false; onlineProfile = "Войдите или создайте аккаунт"; onlineStatus = "Вы вышли из аккаунта"; invalidate() }
                        else -> phase = Phase.READY
                    }
                    invalidate(); return true
                }
                if (phase == Phase.ADMIN) {
                    if (y in 188f..620f) {
                        val idx = ((y - 188f) / 43f).toInt()
                        adminMessage = when (idx) {
                            0 -> admin.toggleEvent()
                            1 -> admin.toggleTournament()
                            in 2..4 -> { adminSelectedPlayer = idx - 2; admin.toggleBan(adminSelectedPlayer) }
                            5 -> admin.grantCoins(adminSelectedPlayer, 500)
                            6 -> admin.setAnnouncement("Рыболовы, удачной рыбалки! Событие скоро начнётся.")
                            7 -> "В журнале: ${admin.auditLines().size} записей"
                            else -> "Закрываем панель"
                        }
                        admin.audit(adminMessage)
                        if (idx == 8) phase = Phase.READY
                    } else if (y > 620f) phase = Phase.READY
                    invalidate(); return true
                }
                if (phase == Phase.SHOP) {
                    if(y in 230f..675f){
                        val idx=((y-230f)/49f).toInt()
                        when(idx){
                            in 0..3 -> { val cost=listOf(0,8,12,10)[idx]; if(coins>=cost){ coins-=cost; baitStock[idx]+=10; selectedBait=idx; message="Куплено: ${baits[idx]} ×10" } else message="Недостаточно монет" }
                            4 -> { val cost=gearCost(rodLevel); if(rodLevel<5 && coins>=cost){coins-=cost;rodLevel++;message="Удилище улучшено до $rodLevel уровня"}else message="Недостаточно монет или максимальный уровень" }
                            5 -> { val cost=gearCost(reelLevel); if(reelLevel<5 && coins>=cost){coins-=cost;reelLevel++;message="Катушка улучшена до $reelLevel уровня"}else message="Недостаточно монет или максимальный уровень" }
                            6 -> { val cost=gearCost(lineLevel); if(lineLevel<5 && coins>=cost){coins-=cost;lineLevel++;message="Леска улучшена до $lineLevel уровня"}else message="Недостаточно монет или максимальный уровень" }
                            7 -> { phase = Phase.GROUNDBAIT; invalidate(); return true }
                            8 -> { phase = Phase.TACKLE; invalidate(); return true }
                        }
                    }
                    save(); phase=Phase.READY; invalidate(); return true
                }
                if (phase == Phase.GROUNDBAIT) {
                    if (y in 225f..590f) {
                        val idx = ((y - 225f) / 112f).toInt()
                        val rowY = 225f + idx * 112f
                        val mix = GroundbaitMix.values().getOrNull(idx)
                        if (mix != null && y < rowY + 54f) {
                            if (groundbaitStock[idx] > 0) {
                                selectedGroundbait = idx
                                prefs.edit().putInt("selected_groundbait", idx).apply()
                                message = "Выбрана прикормка: ${mix.title}"
                            } else message = "Сначала купите эту смесь"
                        } else if (mix != null) {
                            val result = GroundbaitSystem.buy(groundbaitStock[idx], coins, mix)
                            if (result.purchased) {
                                groundbaitStock[idx] = result.stock
                                coins = result.coinsLeft
                                selectedGroundbait = idx
                                prefs.edit().putInt("groundbait_stock_$idx", result.stock).putInt("selected_groundbait", idx).apply()
                            }
                            message = result.message
                            save()
                        }
                    } else if (y >= 600f) phase = Phase.SHOP
                    invalidate(); return true
                }
                if (phase == Phase.TACKLE) {
                    if (y in 225f..589f) {
                        val idx = ((y - 225f) / 91f).toInt()
                        val part = TacklePart.values().getOrNull(idx)
                        if (part != null) {
                            val result = TackleSystem.upgrade(tackle, part, coins)
                            tackle = result.loadout
                            coins = result.coinsLeft
                            message = result.message
                            save()
                        }
                    } else if (y >= 600f) {
                        phase = Phase.SHOP
                    }
                    invalidate(); return true
                }
                if(phase==Phase.MAP){
                    if(y in 242f..620f){
                        val idx=((y-242f)/54f).toInt()
                        if(idx in locations.indices){
                            location=idx
                            prefs.edit().putStringSet("visited_locations", (prefs.getStringSet("visited_locations", setOf("0")) ?: setOf("0")) + idx.toString()).apply()
                            dailyVisited.add(idx.toString())
                            prefs.edit().putStringSet("daily_visited", dailyVisited).apply()
                            save();message="Вы прибыли: ${locations[idx]}"
                            phase=Phase.READY
                        } else if(idx == locations.size) {
                            phase=Phase.SPOTS
                            invalidate();return true
                        } else phase=Phase.READY
                    } else phase=Phase.READY
                    invalidate();return true
                }
                if(phase==Phase.SPOTS){
                    if(y in 242f..405f){
                        val idx=((y-242f)/54f).toInt()
                        if(idx in fishingSpots.indices){
                            fishingSpot=idx
                            prefs.edit().putInt("fishing_spot", fishingSpot).apply()
                            message="Выбрана точка: ${fishingSpots[idx]}"
                        }
                    }
                    phase=Phase.READY;invalidate();return true
                }
                if(phase==Phase.BAG){
                    when {
                        y in 542f..580f -> sellKeepnet()
                        y in 582f..622f -> phase = Phase.JOURNAL
                        y in 623f..665f -> phase = Phase.ACHIEVEMENTS
                        else -> phase = Phase.READY
                    }
                    invalidate(); return true
                }
                if (phase == Phase.JOURNAL || phase == Phase.ACHIEVEMENTS) { phase = Phase.BAG; invalidate(); return true }
                if (phase == Phase.TOURNAMENT) {
                    when {
                        y in 500f..570f && (if (remoteTournamentEnabled) !onlineTournamentJoined else !tournamentState.joined) -> {
                            val token = sessionStore.load()
                            val client = onlineApi
                            if (remoteTournamentEnabled && token != null && client != null) {
                                onlineBusy = true; onlineTournamentStatus = "отправка заявки…"
                                client.joinTournament(token) { result ->
                                    onlineBusy = false
                                    if (result.ok) {
                                        onlineTournamentJoined = true
                                        onlineTournamentStatus = "участие подтверждено сервером"
                                        tournamentState = TournamentSystem.join(tournamentState); saveTournamentState()
                                        message = "Заявка на онлайн-турнир принята"
                                        refreshOnlineData()
                                    } else { onlineTournamentStatus = result.error ?: "не удалось вступить"; message = onlineTournamentStatus }
                                    invalidate()
                                }
                            } else if (remoteTournamentEnabled) {
                                message = "Войди в онлайн-клуб, чтобы вступить в серверный турнир"
                            } else {
                                tournamentState = TournamentSystem.join(tournamentState)
                                saveTournamentState()
                                message = "Ты участвуешь в локальном турнире недели!"
                            }
                        }
                        y in 575f..635f -> {
                            tournamentKind = if (tournamentKind == TournamentKind.HEAVIEST) TournamentKind.TOTAL_WEIGHT else TournamentKind.HEAVIEST
                        }
                        else -> phase = Phase.READY
                    }
                    invalidate(); return true
                }
                if (phase == Phase.QUESTS) {
                    if (y in 190f..505f) {
                        val idx = ((y - 190f) / 105f).toInt()
                        val goals = listOf(dailyCatches >= 3, dailySales >= 2, dailyVisited.size >= 2)
                        val keys = listOf("daily_claim_catch", "daily_claim_sale", "daily_claim_visit")
                        val rewards = listOf(35, 45, 40)
                        if (idx in goals.indices && goals[idx] && !prefs.getBoolean(keys[idx], false)) {
                            coins += rewards[idx]
                            prefs.edit().putBoolean(keys[idx], true).apply()
                            message = "Награда за задание: +${rewards[idx]} монет"
                            save()
                        }
                    } else if (y > 640f) phase = Phase.READY
                    invalidate(); return true
                }
                if (y in 115f..165f) { resetWinterEventProgressIfNeeded(); phase = Phase.WINTER_EVENT; invalidate(); return true }
                if (x > 340f && y < 60f) { phase = Phase.ADMIN; invalidate(); return true }
                if (x in 95f..180f && y < 60f) { phase = Phase.ONLINE; if (sessionStore.load() != null) refreshOnlineData(); invalidate(); return true }
                if (x in 258f..338f && y < 60f) { phase = Phase.QUESTS; invalidate(); return true }
                if (x in 180f..255f && y < 60f) { phase = Phase.TOURNAMENT; invalidate(); return true }
                if(x in 10f..95f && y>720f){phase=Phase.MAP;invalidate();return true}
                if(x in 96f..180f && y>720f){phase=Phase.BAG;invalidate();return true}
                if(x in 181f..265f && y>720f){phase=Phase.SHOP;invalidate();return true}
                if(x>265f && y>720f){ if(phase!=Phase.FIGHT) actionButton();invalidate();return true }
                if(phase==Phase.READY || phase==Phase.CAUGHT){ actionButton();invalidate();return true }
                if(phase==Phase.BITE){startFight();invalidate();return true}
                return true
            }
        };return true
    }
    private fun actionButton(){when(phase){
        Phase.CAUGHT -> {
            val keepnet = loadKeepnet().toMutableSet()
            keepnet.add("$lastCatchName|${"%.3f".format(java.util.Locale.US, fishWeight)}|$lastCatchValue|${System.currentTimeMillis()}-${Random.nextInt(100000)}-${lastTrophyGrade.code}")
            prefs.edit().putStringSet("keepnet", keepnet).putInt("last_catch_value", 0).apply()
            message = "$lastCatchName помещена в садок"
            lastCatchValue = 0
            phase = Phase.READY
            save()
        }
        Phase.READY->{ if(baitStock[selectedBait]<=0){message="Наживка закончилась. Загляните в лавку";return}; baitStock[selectedBait]--
            activeGroundbait = selectedGroundbait.takeIf { it in groundbaitStock.indices && groundbaitStock[it] > 0 }?.let { idx -> groundbaitStock[idx]--; prefs.edit().putInt("groundbait_stock_$idx", groundbaitStock[idx]).apply(); GroundbaitMix.values()[idx] }
            phase=Phase.WAITING;castAt=System.currentTimeMillis();nextBiteDelay=(newBiteDelay() * tackle.biteDelayMultiplier * if (activeGroundbait != null) 0.88f else 1f).toLong();message=if(activeGroundbait!=null) "Заброс с прикормкой: ${activeGroundbait!!.title}" else "Заброс выполнен. Следите за поплавком";save() };Phase.BITE->{startFight()};Phase.FIGHT->{message="Удерживайте кнопку, контролируя натяжение"};else->Unit}}
    private fun startFight() {
        phase = Phase.FIGHT
        fightTime = 0f
        currentFish = FishingCatalog.choose(location, selectedBait, fishingSpot, activeGroundbait)
        fishName = currentFish!!.name
        tension = .28f
        fishProgress = 0f
        fightEngine = FishFightEngine(
            difficulty = currentFish!!.difficulty,
            rodLevel = rodLevel,
            reelLevel = reelLevel,
            lineLevel = lineLevel,
            weatherTensionMultiplier = conditions.tensionMultiplier,
            hookEfficiency = tackle.hookEfficiency,
            lineStrengthMultiplier = tackle.lineStrength
        )
        message = "$fishName сопротивляется! Следите за рывками и не допускайте провисания лески"
    }


    private fun finishCatch() {
        val fish = currentFish ?: return
        phase = Phase.CAUGHT
        reeling = false
        fightEngine = null
        fishWeight = (Random.nextDouble(fish.minKg, fish.maxKg) * conditions.weightMultiplier * FishingTimeSeason.now().weightMultiplier).coerceIn(fish.minKg, fish.maxKg)
        message = "Вы поймали: $fishName"
        catches++
        xp += (fishWeight * 12).toInt()
        dailyCatches++
        lastTrophyGrade = TrophySystem.evaluate(fish, fishWeight).grade
        tournamentState = TournamentSystem.recordCatch(tournamentState, fishName, fishWeight, lastTrophyGrade)
        saveTournamentState()
        recordWinterEventCatch()
        lastCatchName = fishName
        lastCatchWeight = fishWeight
        lastCatchValue = TrophySystem.saleValue(FishingMarket.todayPrice(fishName, fishWeight), lastTrophyGrade)
        val fishIndex = FishingCatalog.fish.indexOfFirst { it.name == fishName }
        val edit = prefs.edit()
            .putInt("daily_catches", dailyCatches)
            .putString("last_catch_name", lastCatchName)
            .putFloat("last_catch_weight", fishWeight.toFloat())
            .putInt("last_catch_value", lastCatchValue)
            .putString("last_trophy_grade", lastTrophyGrade.code)
        val bestOverall = prefs.getFloat("best_fish_weight", 0f)
        if (fishWeight > bestOverall) edit.putFloat("best_fish_weight", fishWeight.toFloat())
        if (lastTrophyGrade != TrophyGrade.NONE) {
            edit.putInt("trophy_total", prefs.getInt("trophy_total", 0) + 1)
            if (lastTrophyGrade == TrophyGrade.LEGENDARY) edit.putInt("trophy_legendary", prefs.getInt("trophy_legendary", 0) + 1)
        }
        if (fishIndex >= 0) {
            edit.putInt("fish_count_$fishIndex", prefs.getInt("fish_count_$fishIndex", 0) + 1)
                .putFloat("fish_best_$fishIndex", max(prefs.getFloat("fish_best_$fishIndex", 0f), fishWeight.toFloat()))
            if (lastTrophyGrade != TrophyGrade.NONE) edit.putInt("fish_trophies_$fishIndex", prefs.getInt("fish_trophies_$fishIndex", 0) + 1)
        }
        message = if (lastTrophyGrade != TrophyGrade.NONE) "${lastTrophyGrade.title}! $fishName — ${String.format(java.util.Locale.US, "%.2f", fishWeight)} кг" else "Вы поймали: $fishName"
        edit.apply()
        save()
        submitOnlineCatch()
    }

    private fun loadKeepnet(): Set<String> = prefs.getStringSet("keepnet", emptySet()) ?: emptySet()

    private fun sellKeepnet() {
        val fish = loadKeepnet()
        if (fish.isEmpty()) {
            message = "Садок пуст"
            return
        }
        val total = fish.sumOf { entry ->
            val parts = entry.split('|')
            val name = parts.getOrNull(0).orEmpty()
            val weight = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            if (weight > 0.0) {
                val gradeCode = parts.getOrNull(3)?.substringAfterLast('-')
                val grade = TrophyGrade.values().firstOrNull { it.code == gradeCode } ?: TrophyGrade.NONE
                TrophySystem.saleValue(FishingMarket.todayPrice(name, weight), grade)
            } else (parts.getOrNull(2)?.toIntOrNull() ?: 0)
        }
        coins += total
        dailySales += fish.size
        prefs.edit().putStringSet("keepnet", emptySet())
            .putInt("fish_sales", prefs.getInt("fish_sales", 0) + fish.size)
            .putInt("daily_sales", dailySales).apply()
        message = "Продано рыб: ${fish.size}, выручка: $total монет"
        save()
    }

    private fun drawBag(c: Canvas) {
        val fish = loadKeepnet()
        val total = fish.sumOf { entry ->
            val parts = entry.split('|')
            val weightKg = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            if (weightKg > 0.0) {
                val gradeCode = parts.getOrNull(3)?.substringAfterLast('-')
                val grade = TrophyGrade.values().firstOrNull { it.code == gradeCode } ?: TrophyGrade.NONE
                TrophySystem.saleValue(FishingMarket.todayPrice(parts.getOrNull(0).orEmpty(), weightKg), grade)
            } else (parts.getOrNull(2)?.toIntOrNull() ?: 0)
        }
        val weight = fish.sumOf { it.split('|').getOrNull(1)?.toDoubleOrNull() ?: 0.0 }
        p.color = Color.argb(245, 9, 19, 17)
        c.drawRoundRect(18f, 175f, 402f, 680f, 16f, 16f, p)
        drawText(c, "САДОК И СНАРЯЖЕНИЕ", 34f, 214f, 19f, Color.rgb(255, 214, 133))
        val recentFish = fish.toList().sortedByDescending { it.substringAfterLast('|').substringBefore('-').toLongOrNull() ?: 0L }.take(2)
        val recentLabels = recentFish.map { entry ->
            val parts = entry.split('|')
            val kg = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val gradeCode = parts.getOrNull(3)?.substringAfterLast('-')
            val grade = TrophyGrade.values().firstOrNull { it.code == gradeCode } ?: TrophyGrade.NONE
            "• ${parts.firstOrNull() ?: "Рыба"} — ${"%.2f".format(java.util.Locale.US, kg)} кг${if (grade != TrophyGrade.NONE) " • ${grade.title}" else ""}"
        }
        val rows = listOf(
            "В садке: ${fish.size} рыб • ${"%.2f".format(java.util.Locale.US, weight)} кг",
            "Рынок сегодня: $total монет • цены меняются ежедневно",
            "Последний улов: $lastCatchName",
            "Рекорд: ${"%.2f".format(java.util.Locale.US, prefs.getFloat("best_fish_weight", 0f))} кг",
            "Опыт: $xp • Монеты: $coins",
            "Наживка: ${baits[selectedBait]} (${baitStock[selectedBait]})",
            recentLabels.getOrNull(0) ?: "Недавний улов: пока пусто",
            recentLabels.getOrNull(1) ?: "В садке пока мало рыбы",
            "ПРОДАТЬ ВСЁ ИЗ САДКА",
            "ЖУРНАЛ РЫБ — нажмите сюда",
            "ДОСТИЖЕНИЯ — нажмите сюда"
        )
        rows.forEachIndexed { i, row ->
            val y = 222f + i * 40f
            p.color = if (i == 8) Color.rgb(105, 67, 35) else Color.rgb(45, 65, 50)
            c.drawRoundRect(34f, y, 386f, y + 35f, 7f, 7f, p)
            drawText(c, row, 46f, y + 23f, 11f, Color.WHITE)
        }
    }

    private fun drawAchievements(c: Canvas) {
        val items = listOf(
            Triple("Первый улов", "Поймать первую рыбу", catches >= 1),
            Triple("Десятка", "Поймать 10 рыб", catches >= 10),
            Triple("Опытный рыбак", "Поймать 50 рыб", catches >= 50),
            Triple("Коллекционер", "Поймать 5 разных видов", FishingCatalog.fish.withIndex().count { (i, _) -> prefs.getInt("fish_count_$i", 0) > 0 } >= 5),
            Triple("Трофей", "Поймать рыбу тяжелее 5 кг", prefs.getFloat("best_fish_weight", 0f) >= 5f),
            Triple("Путешественник", "Посетить все водоёмы", (prefs.getStringSet("visited_locations", setOf("0"))?.size ?: 0) >= locations.size),
            Triple("Мастер снастей", "Улучшить любую снасть до 5 уровня", maxOf(rodLevel, reelLevel, lineLevel) >= 5),
            Triple("Торговец", "Продать первую рыбу", prefs.getInt("fish_sales", 0) >= 1),
            Triple("Охотник за трофеями", "Поймать 5 трофейных рыб", prefs.getInt("trophy_total", 0) >= 5),
            Triple("Легенда водоёма", "Поймать легендарный экземпляр", prefs.getInt("trophy_legendary", 0) >= 1)
        )
        p.color = Color.argb(245, 8, 19, 18); c.drawRoundRect(12f, 120f, 408f, 690f, 14f, 14f, p)
        drawText(c, "ДОСТИЖЕНИЯ", 28f, 153f, 20f, Color.rgb(255, 214, 133))
        val unlocked = items.count { it.third }
        drawText(c, "$unlocked / ${items.size} открыто", 290f, 153f, 12f, Color.LTGRAY)
        items.forEachIndexed { i, item ->
            val y = 174f + i * 47f
            p.color = if (item.third) Color.rgb(49, 82, 52) else Color.rgb(37, 48, 46)
            c.drawRoundRect(24f, y, 396f, y + 40f, 7f, 7f, p)
            drawText(c, (if (item.third) "✓ " else "- ") + item.first, 36f, y + 16f, 12f, if (item.third) Color.rgb(255, 220, 140) else Color.WHITE)
            drawText(c, item.second, 36f, y + 32f, 9f, Color.LTGRAY)
        }
        button(c, 145f, 665f, 130f, 48f, "НАЗАД", false)
    }

    private fun drawDailyQuests(c: Canvas) {
        val tasks = listOf(
            Triple("Удачный день", "Поймать 3 рыбы", dailyCatches, 3),
            Triple("На рынке", "Продать 2 рыбы", dailySales, 2),
            Triple("Путешествие", "Посетить 2 водоёма", dailyVisited.size, 2)
        )
        val keys = listOf("daily_claim_catch", "daily_claim_sale", "daily_claim_visit")
        val rewards = listOf(35, 45, 40)
        p.color = Color.argb(245, 8, 19, 18)
        c.drawRoundRect(12f, 120f, 408f, 650f, 14f, 14f, p)
        drawText(c, "ЕЖЕДНЕВНЫЕ ЗАДАНИЯ", 28f, 153f, 19f, Color.rgb(255, 214, 133))
        drawText(c, "Обновление при смене календарного дня", 28f, 176f, 10f, Color.LTGRAY)
        tasks.forEachIndexed { i, task ->
            val y = 190f + i * 105f
            val complete = task.third >= task.fourth
            val claimed = prefs.getBoolean(keys[i], false)
            p.color = if (claimed) Color.rgb(38, 54, 45) else if (complete) Color.rgb(58, 86, 49) else Color.rgb(37, 48, 46)
            c.drawRoundRect(24f, y, 396f, y + 88f, 8f, 8f, p)
            drawText(c, task.first, 38f, y + 24f, 14f, Color.WHITE)
            drawText(c, task.second, 38f, y + 47f, 12f, Color.LTGRAY)
            drawText(c, "${task.third.coerceAtMost(task.fourth)} / ${task.fourth}   •   +${rewards[i]} монет", 38f, y + 70f, 11f, Color.rgb(255, 220, 150))
            drawText(c, if (claimed) "ПОЛУЧЕНО" else if (complete) "ЗАБРАТЬ" else "В ПРОЦЕССЕ", 300f, y + 24f, 10f, if (claimed) Color.LTGRAY else Color.rgb(255, 220, 150))
        }
        button(c, 145f, 665f, 130f, 48f, "НАЗАД", false)
    }

    private fun newBiteDelay(): Long {
        val cycle = FishingTimeSeason.now().biteMultiplier
        val factor = conditions.biteMultiplier * cycle
        val low = (9000 * factor).toLong().coerceAtLeast(1500L)
        val high = (17000 * factor).toLong().coerceAtLeast(low + 1L)
        return Random.nextLong(low, high)
    }
    private fun gearCost(level:Int)=level*level*45
    private fun save(){ val e=prefs.edit().putInt("coins",coins).putInt("xp",xp).putInt("catches",catches).putInt("location",location).putInt("bait",selectedBait).putInt("rod_level",rodLevel).putInt("reel_level",reelLevel).putInt("line_level",lineLevel).putInt("tackle_float", tackle.floatIndex).putInt("tackle_hook", tackle.hookIndex).putInt("tackle_sinker", tackle.sinkerIndex).putInt("tackle_line", tackle.lineIndex); baitStock.forEachIndexed{i,v->e.putInt("bait_stock_$i",v)}; groundbaitStock.forEachIndexed{i,v->e.putInt("groundbait_stock_$i",v)}; e.apply() }
    override fun onDetachedFromWindow(){handler.removeCallbacks(frameTask);super.onDetachedFromWindow()}
}
