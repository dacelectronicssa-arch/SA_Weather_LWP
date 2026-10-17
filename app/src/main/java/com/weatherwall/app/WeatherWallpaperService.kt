package com.weatherwall.app

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.weatherwall.app.render.Holidays
import com.weatherwall.app.render.MarineLife
import com.weatherwall.app.render.MilkyWay
import com.weatherwall.app.render.Scenes
import com.weatherwall.app.render.SceneConfig
import com.weatherwall.app.render.SkyTime
import com.weatherwall.app.render.WeatherRenderer
import java.util.concurrent.atomic.AtomicBoolean

class WeatherWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WeatherEngine()

    inner class WeatherEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs = Prefs.get(this@WeatherWallpaperService)
        private val repo = WeatherRepository(this@WeatherWallpaperService)
        private val renderer = WeatherRenderer(resources.displayMetrics.density)

        private var visible = false
        // Battery: no animation while the phone is locked (screen off or on the lock screen).
        private var locked = false
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> setLocked(true)
                    Intent.ACTION_USER_PRESENT -> setLocked(false)
                    Intent.ACTION_SCREEN_ON -> setLocked(keyguard?.isKeyguardLocked ?: false)
                }
            }
        }
        private var lastFrameAt = 0L
        private var useHardwareCanvas = true
        private var lastAttemptAt = 0L
        private val fetching = AtomicBoolean(false)
        private val perf: PerfMeter? =
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) PerfMeter { renderer.perfLabel() } else null

        private val drawRunnable = Runnable { drawFrame() }
        private val weatherCheck = object : Runnable {
            override fun run() {
                maybeRefresh(force = false)
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, filter)
            locked = keyguard?.isKeyguardLocked ?: false
            applyDebugTimeOffset()
            renderer.maxFps = Prefs.maxFps(this@WeatherWallpaperService)
            applyFireworksPreview()
            applyWeather(animate = false)
        }

        override fun onDestroy() {
            handler.removeCallbacksAndMessages(null)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            runCatching { unregisterReceiver(screenReceiver) }
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.setSize(width, height)
            if (visible) scheduleFrame(0) // one frame even when locked, so the surface isn't blank
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawRunnable)
            handler.removeCallbacks(weatherCheck)
            super.onSurfaceDestroyed(holder)
        }

        // Hidden behind a full-screen app, or the screen is off: Android tells us we're not
        // visible and all drawing stops until we are again.
        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            Log.i(TAG, if (isVisible) "Visible${if (locked) " (locked: still frame)" else ""}" else "Hidden: paused")
            restart()
        }

        private fun setLocked(value: Boolean) {
            if (value == locked) return
            locked = value
            Log.i(TAG, if (locked) "Locked: paused" else "Unlocked: resumed")
            restart()
        }

        /** Animates only while visible and unlocked; when locked but visible, shows one still frame. */
        private fun restart() {
            handler.removeCallbacks(drawRunnable)
            handler.removeCallbacks(weatherCheck)
            if (!visible) return
            lastFrameAt = SystemClock.uptimeMillis()
            scheduleFrame(0)
            if (!locked) {
                renderer.onVisible()
                handler.post(weatherCheck)
            }
        }

        // Called on the main thread whenever the settings screen or a fetch writes prefs.
        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            when (key) {
                Prefs.KEY_FETCHED_AT, Prefs.KEY_OVERRIDE, Prefs.KEY_SCENE -> {
                    // Back to live weather ends the demo's photo-style Milky Way.
                    if (prefs.getString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO) == Prefs.OVERRIDE_AUTO) MilkyWay.heroView = false
                    applyFireworksPreview()
                    applyWeather(animate = true)
                }
                Prefs.KEY_CITY -> maybeRefresh(force = true)
                Prefs.KEY_MAX_FPS -> renderer.maxFps = Prefs.maxFps(this@WeatherWallpaperService)
                Prefs.KEY_DEBUG_PLANE -> renderer.launchPlaneNow()
                Prefs.KEY_DEBUG_SHIP -> renderer.launchShipNow()
                Prefs.KEY_DEBUG_BIRDS -> renderer.launchBirdsNow()
                Prefs.KEY_DEBUG_LIONS -> renderer.launchLionsNow()
                Prefs.KEY_DEBUG_GEESE -> renderer.launchGeeseNow()
                Prefs.KEY_DEBUG_JET -> renderer.launchJetNow()
                Prefs.KEY_DEBUG_PIGEONS -> renderer.launchPigeonsNow()
                Prefs.KEY_DEBUG_FLYPAST -> renderer.launchFlypastNow()
                Prefs.KEY_DEBUG_DEMO -> runDemo()
                Prefs.KEY_PREVIEW_HOLIDAY -> applyFireworksPreview()
                Prefs.KEY_DEBUG_FIREWORKS -> {
                    val v = prefs.getString(Prefs.KEY_DEBUG_FIREWORKS, "off") ?: "off"
                    renderer.forcedFinale = v == "finale"
                    renderer.forcedHoliday = if (v == "finale") Holidays.NEW_YEARS_EVE else Holidays.ALL.firstOrNull { it.name.equals(v, ignoreCase = true) }
                }
                Prefs.KEY_DEBUG_BOAT -> renderer.launchBoatNow(prefs.getString(Prefs.KEY_DEBUG_BOAT, "")?.substringBefore(':') ?: "")
                Prefs.KEY_DEBUG_SEA -> {
                    val name = prefs.getString(Prefs.KEY_DEBUG_SEA, "")?.substringBefore(':')?.uppercase()
                    renderer.launchSeaLifeNow(MarineLife.Kind.entries.firstOrNull { it.name == name })
                }
                Prefs.KEY_DEBUG_TIME_OFFSET -> {
                    applyDebugTimeOffset()
                    applyWeather(animate = true)
                }
            }
        }

        /**
         * Debug: a 60-second demo for videos (Cape Town or Jhb City, whichever is selected). Every visitor in quick succession (a few
         * seconds apart instead of minutes) while the weather steps through sun, cloud, fog, rain
         * and a thunderstorm to a clear 3am night under the Milky Way. Runs inside the wallpaper,
         * so nothing else shows on screen.
         */
        private fun runDemo() {
            handler.removeCallbacksAndMessages(DEMO)
            val now = System.currentTimeMillis()
            val afternoon = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 14); set(java.util.Calendar.MINUTE, 0)
            }.timeInMillis
            // A moonless winter night, the galactic core high overhead.
            val milkyWayNight = java.util.Calendar.getInstance().apply {
                set(2027, java.util.Calendar.JUNE, 4, 3, 0, 0)
            }.timeInMillis
            fun weather(condition: String, at: Long): () -> Unit = {
                prefs.edit()
                    .putString(Prefs.KEY_OVERRIDE, condition)
                    .putFloat(Prefs.KEY_DEBUG_TIME_OFFSET, (at - now) / 3_600_000f)
                    .apply()
            }
            MilkyWay.heroView = false
            val night: () -> Unit = { MilkyWay.heroView = true; weather("CLEAR:night", milkyWayNight)() }
            val scene = Prefs.scene(this@WeatherWallpaperService)
            val steps = if (scene == Scenes.SAVANNA) listOf<Pair<Long, () -> Unit>>(
                // Savanna: a sped-up lion visit (stalk in, herd scatters with springbok pronking,
                // vultures circle down to the acacia, lions rest and leave, herd returns), geese
                // and hadedas, then the night.
                0L to weather("CLEAR:day", afternoon),
                2_000L to { renderer.launchLionsNow(fast = true) },
                5_000L to { renderer.launchGeeseNow() },
                8_000L to weather("PARTLY_CLOUDY:day", afternoon),
                16_000L to weather("CLOUDY:day", afternoon),
                20_000L to { renderer.launchBirdsNow() },
                20_500L to weather("OVERCAST:day", afternoon),
                24_000L to weather("FOG:day", afternoon),
                30_000L to weather("RAIN:day", afternoon),
                33_000L to { renderer.launchGeeseNow() },
                38_000L to weather("THUNDERSTORM:day", afternoon),
                46_000L to night,
            ) else if (scene == Scenes.ALTRON) listOf<Pair<Long, () -> Unit>>(
                // Altron head office: the sky's weather over the building, hadedas, and the
                // lights coming on for the night.
                0L to weather("CLEAR:day", afternoon),
                3_000L to { renderer.launchBirdsNow() },
                8_000L to weather("PARTLY_CLOUDY:day", afternoon),
                16_000L to weather("CLOUDY:day", afternoon),
                20_500L to weather("OVERCAST:day", afternoon),
                24_000L to weather("FOG:day", afternoon),
                30_000L to weather("RAIN:day", afternoon),
                38_000L to weather("THUNDERSTORM:day", afternoon),
                46_000L to night,
            ) else if (scene == Scenes.JHB) listOf<Pair<Long, () -> Unit>>(
                // Jhb City: a pigeon flock's full visit (arrive, wheel, rooftops, leave) spans the
                // minute; 737s in different liveries keep crossing; hadedas twice.
                0L to weather("CLEAR:day", afternoon),
                500L to { renderer.launchPigeonsNow() },
                2_000L to { renderer.launchJetNow() },
                6_000L to { renderer.launchBirdsNow() },
                8_000L to weather("PARTLY_CLOUDY:day", afternoon),
                10_000L to { renderer.launchJetNow() },
                16_000L to weather("CLOUDY:day", afternoon),
                18_000L to { renderer.launchJetNow() },
                20_500L to weather("OVERCAST:day", afternoon),
                24_000L to weather("FOG:day", afternoon),
                27_000L to { renderer.launchJetNow() },
                30_000L to weather("RAIN:day", afternoon),
                34_000L to { renderer.launchJetNow() },
                38_000L to weather("THUNDERSTORM:day", afternoon),
                40_000L to { renderer.launchBirdsNow() },
                46_000L to night,
                48_000L to { renderer.launchJetNow() },
            ) else listOf<Pair<Long, () -> Unit>>(
                0L to weather("CLEAR:day", afternoon),
                1_000L to { renderer.launchBirdsNow() },
                3_000L to { renderer.launchBoatNow("yacht") },
                5_000L to { renderer.launchPlaneNow() },
                8_000L to weather("PARTLY_CLOUDY:day", afternoon),
                9_000L to { renderer.launchSeaLifeNow(MarineLife.Kind.TAIL) },
                12_000L to { renderer.launchShipNow() },
                16_000L to weather("CLOUDY:day", afternoon),
                17_000L to { renderer.launchBoatNow("jetski") },
                19_000L to { renderer.launchFlypastNow() },
                20_500L to weather("OVERCAST:day", afternoon),
                24_000L to weather("FOG:day", afternoon),
                30_000L to weather("RAIN:day", afternoon),
                31_000L to { renderer.launchSeaLifeNow(MarineLife.Kind.DOLPHINS) },
                38_000L to weather("THUNDERSTORM:day", afternoon),
                46_000L to night,
                47_000L to { renderer.launchBirdsNow() },
            )
            for ((delay, step) in steps) handler.postAtTime({ step() }, DEMO, SystemClock.uptimeMillis() + delay)
            Log.i(TAG, "Demo sequence started")
        }

        /** The "Fireworks: random holiday" preview: that holiday's fireworks on a clear night. */
        private var previewingFireworks = false

        private fun applyFireworksPreview() {
            val on = prefs.getString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO) == Prefs.OVERRIDE_FIREWORKS
            if (on) {
                val name = prefs.getString(Prefs.KEY_PREVIEW_HOLIDAY, null)
                renderer.forcedHoliday = Holidays.ALL.firstOrNull { it.name == name } ?: Holidays.ALL.random()
                renderer.forcedFinale = false
            } else if (previewingFireworks) {
                renderer.forcedHoliday = null
            }
            renderer.holidayPlaneAtNight = on
            previewingFireworks = on
        }

        private fun applyDebugTimeOffset() {
            val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val hours = if (debuggable) prefs.getFloat(Prefs.KEY_DEBUG_TIME_OFFSET, 0f) else 0f
            SkyTime.offsetMs = (hours * 3_600_000L).toLong()
        }

        private fun applyWeather(animate: Boolean) {
            val weather = Prefs.effectiveWeather(this@WeatherWallpaperService)
            val latitude = Prefs.latitude(this@WeatherWallpaperService)
            val longitude = Prefs.longitude(this@WeatherWallpaperService)
            renderer.setConfig(SceneConfig.forWeather(weather, latitude, longitude, Prefs.scene(this@WeatherWallpaperService)), animate)
        }

        private fun maybeRefresh(force: Boolean) {
            val now = System.currentTimeMillis()
            val age = now - prefs.getLong(Prefs.KEY_FETCHED_AT, 0L)
            if (!force && age < REFRESH_INTERVAL_MS) return
            if (!force && now - lastAttemptAt < RETRY_INTERVAL_MS) return
            if (!fetching.compareAndSet(false, true)) return
            lastAttemptAt = now
            Thread {
                repo.refresh().onFailure { Log.w(TAG, "Weather refresh failed", it) }
                fetching.set(false)
            }.start()
        }

        private fun scheduleFrame(delayMs: Long) {
            handler.removeCallbacks(drawRunnable)
            handler.postDelayed(drawRunnable, delayMs)
        }

        private fun drawFrame() {
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastFrameAt) / 1000f).coerceIn(0f, 0.15f)
            lastFrameAt = now
            perf?.begin()
            renderer.update(dt)
            perf?.updated()

            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = lockCanvas(holder)
                if (canvas != null) renderer.draw(canvas)
                perf?.drawn()
            } catch (e: Exception) {
                Log.w(TAG, "Frame failed", e)
            } finally {
                if (canvas != null) {
                    runCatching { holder.unlockCanvasAndPost(canvas) }
                    perf?.posted()
                }
            }
            // Battery: draw only as often as what's on screen needs, allowing for this frame's cost.
            val interval = 1000L / renderer.desiredFps()
            val spent = SystemClock.uptimeMillis() - now
            if (visible && !locked) scheduleFrame((interval - spent).coerceAtLeast(0L))
        }

        private fun lockCanvas(holder: SurfaceHolder): Canvas? {
            if (useHardwareCanvas) {
                try {
                    return holder.lockHardwareCanvas()
                } catch (e: Exception) {
                    Log.i(TAG, "Hardware canvas unavailable, falling back to software", e)
                    useHardwareCanvas = false
                }
            }
            return holder.lockCanvas()
        }
    }

    private companion object {
        const val TAG = "WeatherWallpaper"
        val DEMO = Any() // token for the demo's scheduled steps
        const val CHECK_INTERVAL_MS = 60_000L
        const val REFRESH_INTERVAL_MS = 30 * 60_000L
        const val RETRY_INTERVAL_MS = 5 * 60_000L
    }
}
