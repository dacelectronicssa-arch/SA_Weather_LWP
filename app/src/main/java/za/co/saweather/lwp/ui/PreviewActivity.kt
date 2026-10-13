package za.co.saweather.lwp.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.SettingsStore
import za.co.saweather.lwp.TiltSensor
import za.co.saweather.lwp.TimeMode
import za.co.saweather.lwp.WeatherWallpaperService
import za.co.saweather.lwp.engine.SurfaceDriver

/** Launcher screen: a live, full-screen preview with scene picker, time scrubber and set-wallpaper. */
class PreviewActivity : Activity(), SurfaceHolder.Callback, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var driver: SurfaceDriver
    private lateinit var tilt: TiltSensor
    private lateinit var controls: LinearLayout
    private lateinit var timeLabel: TextView
    private val chips = HashMap<SceneId, TextView>()
    /** Preview-only time override in hours; null follows the saved time setting. */
    private var scrubHour: Float? = null
    private var demo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        val surface = SurfaceView(this)
        surface.holder.addCallback(this)
        root.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        driver = SurfaceDriver(surface.holder, "sa-weather-preview")
        tilt = TiltSensor(this) { x, y -> driver.setTilt(x, y) }

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT))
        }
        controls.addView(TextView(this).apply {
            text = "SA Live Weather"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(dp(4), dp(24), 0, dp(8))
        })

        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (s in SceneId.entries) {
            val chip = TextView(this).apply {
                text = s.title
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener { SettingsStore.setScene(this@PreviewActivity, s) }
            }
            chips[s] = chip
            chipRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        controls.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chipRow) })

        timeLabel = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; setPadding(dp(4), dp(14), 0, 0) }
        controls.addView(timeLabel)
        val scrub = SeekBar(this).apply {
            max = 24 * 60
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    scrubHour = p / 60f
                    demo = false
                    pushSettings()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        controls.addView(scrub)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        buttons.addView(button("Live time") { scrubHour = null; demo = false; scrub.progress = 0; pushSettings() })
        buttons.addView(button("Demo cycle") { scrubHour = null; demo = true; pushSettings() })
        buttons.addView(button("Settings") { startActivity(Intent(this, SettingsActivity::class.java)) })
        controls.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(buttons) })
        controls.addView(Button(this).apply {
            text = "Set as live wallpaper"
            setOnClickListener { setWallpaper() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        root.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        surface.setOnClickListener { controls.visibility = if (controls.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        root.setOnApplyWindowInsetsListener { _, insets: WindowInsets ->
            @Suppress("DEPRECATION")
            controls.setPadding(dp(16), dp(12), dp(16), dp(16) + insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pushSettings() {
        var s = SettingsStore.load(this)
        val hour = scrubHour
        s = when {
            hour != null -> s.copy(timeMode = TimeMode.MANUAL, manualHour = hour)
            demo -> s.copy(timeMode = TimeMode.DEMO)
            else -> s
        }
        driver.applySettings(s)
        timeLabel.text = when {
            hour != null -> String.format("Preview time  %02d:%02d", hour.toInt(), ((hour - hour.toInt()) * 60).toInt())
            demo -> "Preview time  demo cycle (${s.demoSpeed.toInt()}x)"
            else -> "Preview time  ${s.timeMode.title.lowercase()} — drag to scrub through the day"
        }
        for ((id, chip) in chips) {
            chip.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (id == s.scene) Color.argb(220, 255, 255, 255) else Color.argb(70, 255, 255, 255))
            }
            chip.setTextColor(if (id == s.scene) Color.BLACK else Color.WHITE)
        }
    }

    private fun setWallpaper() {
        val component = ComponentName(this, WeatherWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        try {
            startActivity(direct)
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                Toast.makeText(this, "Open your launcher's wallpaper settings to choose SA Live Weather", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SettingsStore.prefs(this).registerOnSharedPreferenceChangeListener(this)
        pushSettings()
        driver.setVisible(true)
        if (SettingsStore.load(this).parallaxActive) tilt.start()
    }

    override fun onPause() {
        SettingsStore.prefs(this).unregisterOnSharedPreferenceChangeListener(this)
        driver.setVisible(false)
        tilt.stop()
        super.onPause()
    }

    override fun onDestroy() {
        driver.release()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) = pushSettings()

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = driver.surfaceChanged(width, height).let { }
    override fun surfaceDestroyed(holder: SurfaceHolder) = driver.surfaceDestroyed()
}
