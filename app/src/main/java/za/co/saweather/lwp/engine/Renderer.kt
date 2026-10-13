package za.co.saweather.lwp.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderNode
import android.graphics.Typeface
import android.os.Build
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.TimeMode
import za.co.saweather.lwp.WallpaperSettings
import za.co.saweather.lwp.scenes.Scene
import za.co.saweather.lwp.scenes.Scenes
import java.util.Calendar
import kotlin.math.floor
import kotlin.math.sin

/** Composes the full layer stack for one frame. Not thread-safe: drive from one thread. */
class Renderer {
    val frame = Frame()
    private val sky = Sky()
    private val celestial = Celestial()
    private val weather = Weather()
    private var scene: Scene = Scenes.create(SceneId.CAPE_TOWN)

    private var lastNanos = 0L
    private var startNanos = 0L
    private var demoHours = Double.NaN
    private var demoElapsedHours = 0.0
    private var parTargetX = 0f
    private var parTargetY = 0f
    private var launcherX = 0f

    private val gradePaint = Paint()
    private val accentPaint = Paint()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setShadowLayer(6f, 0f, 2f, Color.argb(140, 0, 0, 0))
    }
    private var node: Any? = null

    var settings = WallpaperSettings()
        private set

    fun applySettings(s: WallpaperSettings) {
        if (s.timeMode != TimeMode.DEMO) demoHours = Double.NaN
        val perfChanged = s.performance != settings.performance
        settings = s
        frame.settings = s
        if (frame.theme.id != s.theme) frame.theme = Theme(s.theme)
        if (!s.rotateScenes) switchScene(s.scene)
        if (perfChanged) {
            // Particle budgets are sized at build time.
            celestial.build(frame, scene.config)
        }
    }

    fun setSize(w: Int, h: Int) {
        frame.w = w.coerceAtLeast(1)
        frame.h = h.coerceAtLeast(1)
    }

    /** Launcher home-screen offset, 0..1. */
    fun setLauncherOffset(x: Float) {
        launcherX = (x - 0.5f) * 2f
    }

    /** Device tilt, roughly -1..1 per axis. */
    fun setTilt(x: Float, y: Float) {
        parTargetX = x
        parTargetY = y
    }

    val currentScene: SceneId get() = scene.id

    private fun switchScene(id: SceneId) {
        if (scene.id == id) return
        scene.release()
        scene = Scenes.create(id)
    }

    fun render(c: Canvas, nowNanos: Long) {
        if (startNanos == 0L) { startNanos = nowNanos; lastNanos = nowNanos }
        val f = frame
        f.dt = ((nowNanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nowNanos
        f.t = ((nowNanos - startNanos) / 1e9).toFloat()

        val (hour, lunarDays) = clock(f.dt)
        if (settings.rotateScenes) {
            val idx = floor((lunarDays * 24.0) / ROTATE_HOURS).toLong()
            switchScene(SceneId.entries[Math.floorMod(idx, SceneId.entries.size.toLong()).toInt()])
        }
        f.scene = scene.id
        val cfg = scene.config
        f.horizonY = f.h * cfg.horizon

        // Parallax: launcher scroll + tilt, eased, plus a slow drift that keeps the scene alive.
        val par = settings.parallaxActive
        val tx = if (par) (launcherX * 0.6f + parTargetX).coerceIn(-1.2f, 1.2f) else 0f
        val ty = if (par) parTargetY.coerceIn(-1f, 1f) else 0f
        val ease = minOf(1f, f.dt * 5f)
        val drift = if (par) sin(f.t * 0.05f) * 0.18f else 0f
        f.parX += (tx + drift - f.parX) * ease
        f.parY += (ty + sin(f.t * 0.037f) * (if (par) 0.12f else 0f) - f.parY) * ease

        f.atmos.update(hour, lunarDays, cfg, settings.moonGlow, f.theme.lockHour)
        scene.adjust(f.atmos)
        scene.ensureBuilt(f)
        celestial.build(f, cfg)
        weather.build(f, cfg)
        weather.update(f, cfg)
        f.beginFrame()
        scene.update(f, weather)

        val accent = f.theme.accentFilter
        if (accent != null && Build.VERSION.SDK_INT >= 29 && c.isHardwareAccelerated) {
            renderNoir(c, accent)
        } else {
            val grade = f.theme.filter
            if (grade != null) {
                gradePaint.colorFilter = grade
                c.saveLayer(null, gradePaint)
            }
            drawLayers(c)
            if (grade != null) c.restore()
        }
        drawOverlay(c)
    }

    private fun renderNoir(c: Canvas, accent: android.graphics.ColorFilter) {
        if (Build.VERSION.SDK_INT < 29) return
        val rn = (node as? RenderNode) ?: RenderNode("frame").also { node = it }
        rn.setPosition(0, 0, frame.w, frame.h)
        val rc = rn.beginRecording()
        drawLayers(rc)
        rn.endRecording()
        gradePaint.colorFilter = frame.theme.filter
        c.saveLayer(null, gradePaint)
        c.drawRenderNode(rn)
        c.restore()
        accentPaint.colorFilter = accent
        c.saveLayer(null, accentPaint)
        c.drawRenderNode(rn)
        c.restore()
    }

    private fun drawLayers(c: Canvas) {
        val f = frame
        val cfg = scene.config
        sky.draw(c, f, cfg)                  // 1 sky gradient
        celestial.draw(c, f, cfg)            //   stars, Milky Way, moon, sun
        weather.drawClouds(c, f, cfg)        //   clouds & storm front
        weather.drawLightning(c, f)          //   bolts land behind the hills
        scene.drawWorld(c, f)                // 2,4,5 landscape, midground, wildlife
        weather.drawParticles(c, f)          // 3 dust, spray, leaves, fireflies
        weather.drawPrecipitation(c, f, cfg) // 3 rain / drizzle / snow
        scene.drawForeground(c, f)           // 6 foreground
        weather.drawAtmosphere(c, f, cfg)    // 7 fog, haze, flash
        scene.drawEmissive(c, f)             //   lights that cut through the haze
    }

    /** Layer 8: optional time / scene name readout. */
    private fun drawOverlay(c: Canvas) {
        val s = settings
        if (!s.showTime && !s.showSceneName) return
        val f = frame
        val th = f.theme
        textPaint.color = withAlpha(th.uiText, th.uiAlpha)
        var y = f.h * 0.11f
        if (s.showTime) {
            textPaint.textSize = 54f * f.u
            val h = f.atmos.hour
            val hh = floor(h).toInt()
            val mm = floor((h - hh) * 60f).toInt()
            c.drawText(String.format("%02d:%02d", hh, mm), 48f * f.u, y, textPaint)
            y += 40f * f.u
            textPaint.textSize = 26f * f.u
            c.drawText(f.atmos.phase.label, 50f * f.u, y, textPaint)
            y += 44f * f.u
        }
        if (s.showSceneName) {
            textPaint.textSize = 30f * f.u
            c.drawText(scene.id.title, 50f * f.u, y, textPaint)
            y += 34f * f.u
            textPaint.textSize = 22f * f.u
            textPaint.color = withAlpha(th.uiText, th.uiAlpha * 0.7f)
            c.drawText(scene.id.subtitle, 50f * f.u, y, textPaint)
        }
    }

    /** @return hour of day (0..24) and days since the reference new moon. */
    private fun clock(dt: Float): Pair<Float, Double> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val realHour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0 +
            cal.get(Calendar.SECOND) / 3600.0 + cal.get(Calendar.MILLISECOND) / 3.6e6
        val realDays = (now - Atmosphere.NEW_MOON_EPOCH_MS) / 86_400_000.0
        return when (settings.timeMode) {
            TimeMode.REAL -> Pair(realHour.toFloat(), realDays)
            TimeMode.MANUAL -> Pair(settings.manualHour, realDays)
            TimeMode.DEMO -> {
                if (demoHours.isNaN()) { demoHours = realHour; demoElapsedHours = 0.0 }
                val adv = dt * settings.demoSpeed / 3600.0 * DEMO_BASE
                demoHours = (demoHours + adv) % 24.0
                demoElapsedHours += adv
                Pair(demoHours.toFloat(), realDays + demoElapsedHours / 24.0)
            }
        }
    }

    fun release() {
        scene.release()
        if (Build.VERSION.SDK_INT >= 29) (node as? RenderNode)?.discardDisplayList()
    }

    companion object {
        /** Scene rotation period in (simulated) hours; not a divisor of 24 so scenes visit every time of day. */
        const val ROTATE_HOURS = 4.0
        /**
         * Demo speed 1x already runs a full day in 24 minutes, so the 1x–120x slider spans
         * "relaxed" to "a full day in 12 seconds".
         */
        const val DEMO_BASE = 60.0
    }
}
