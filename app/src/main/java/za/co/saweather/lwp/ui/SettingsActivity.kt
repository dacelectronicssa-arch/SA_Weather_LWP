package za.co.saweather.lwp.ui

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import za.co.saweather.lwp.Performance
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.SettingsStore
import za.co.saweather.lwp.ThemeId
import za.co.saweather.lwp.TimeMode
import za.co.saweather.lwp.engine.Renderer

/** The settings panel from the brief, built with platform widgets. Changes apply live. */
class SettingsActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Wallpaper settings"
        prefs = SettingsStore.prefs(this)
        val s = SettingsStore.load(this)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))
        }

        section("Scene")
        radios(SceneId.entries.map { it.name to "${it.title} — ${it.subtitle}" }, s.scene.name) { put(SettingsStore.SCENE, it) }
        toggle("Rotate scenes every 4 hours", s.rotateScenes) { put(SettingsStore.ROTATE, it) }
        toggle("Double-tap home screen to change scene", s.doubleTapScene) { put(SettingsStore.DOUBLE_TAP, it) }

        section("Theme")
        radios(ThemeId.entries.map { it.name to it.title }, s.theme.name) { put(SettingsStore.THEME, it) }

        section("Time")
        radios(TimeMode.entries.map { it.name to it.title }, s.timeMode.name) { put(SettingsStore.TIME_MODE, it) }
        val speedLabel = label("")
        fun speedText(v: Int): String {
            val dayMinutes = 24 * 60 / (v * Renderer.DEMO_BASE)
            val pace = if (dayMinutes >= 1.0) "${"%.0f".format(dayMinutes)} min" else "${"%.0f".format(dayMinutes * 60)} s"
            return "Demo cycle speed: ${v}x (a full day every $pace)"
        }
        speedLabel.text = speedText(s.demoSpeed.toInt())
        slider(119, s.demoSpeed.toInt() - 1) { v ->
            speedLabel.text = speedText(v + 1)
            prefs.edit().putFloat(SettingsStore.DEMO_SPEED, (v + 1).toFloat()).apply()
        }
        val manualLabel = label("")
        fun manualText(m: Int) = "Manual time: %02d:%02d".format(m / 60, m % 60)
        manualLabel.text = manualText((s.manualHour * 60).toInt())
        slider(24 * 60 - 1, (s.manualHour * 60).toInt()) { m ->
            manualLabel.text = manualText(m)
            prefs.edit().putFloat(SettingsStore.MANUAL_HOUR, m / 60f).apply()
        }

        section("Celestial")
        toggle("Real lunar phase (synced to date)", s.realLunar) { put(SettingsStore.REAL_LUNAR, it) }
        toggle("Milky Way (when conditions allow)", s.milkyWay) { put(SettingsStore.MILKY_WAY, it) }
        toggle("Shooting stars", s.shootingStars) { put(SettingsStore.SHOOTING, it) }
        toggle("Show constellations (Southern Cross, Scorpius, Orion)", s.constellations) { put(SettingsStore.CONSTELLATIONS, it) }
        toggle("Show planets (Venus, Jupiter, Saturn)", s.planets) { put(SettingsStore.PLANETS, it) }
        toggle("Moon glow affects scene lighting", s.moonGlow) { put(SettingsStore.MOON_GLOW, it) }

        section("Wildlife")
        toggle("Show animals", s.showAnimals) { put(SettingsStore.ANIMALS, it) }
        toggle("Storm behaviour (animals react to weather)", s.stormBehaviour) { put(SettingsStore.STORM_BEHAVIOUR, it) }
        toggle("Show all animals always (ignore day/night rules)", s.allAnimalsAlways) { put(SettingsStore.ALL_ANIMALS, it) }

        section("Performance")
        radios(Performance.entries.map { it.name to "${it.title} (${it.fps} fps)" }, s.performance.name) { put(SettingsStore.PERFORMANCE, it) }
        label("Battery saver also reduces particles and turns off parallax.").setTextColor(Color.GRAY)

        section("Display")
        toggle("Parallax on scroll / gyro", s.parallax) { put(SettingsStore.PARALLAX, it) }
        toggle("Show time overlay", s.showTime) { put(SettingsStore.SHOW_TIME, it) }
        toggle("Show scene name", s.showSceneName) { put(SettingsStore.SHOW_NAME, it) }

        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    private fun put(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun section(title: String) {
        column.addView(TextView(this).apply {
            text = title.uppercase()
            textSize = 13f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(255, 180, 90))
            setPadding(0, dp(24), 0, dp(6))
        })
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(0, dp(10), 0, 0)
    }.also { column.addView(it) }

    private fun toggle(text: String, value: Boolean, onChange: (Boolean) -> Unit) {
        column.addView(Switch(this).apply {
            this.text = text
            textSize = 15f
            isChecked = value
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun radios(options: List<Pair<String, String>>, selected: String, onChange: (String) -> Unit) {
        val group = RadioGroup(this)
        val values = HashMap<Int, String>()
        for (o in options) {
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = o.second
                textSize = 15f
            }
            values[button.id] = o.first
            group.addView(button)
            if (o.first == selected) group.check(button.id)
        }
        group.setOnCheckedChangeListener { _, id -> values[id]?.let(onChange) }
        column.addView(group)
    }

    private fun slider(max: Int, value: Int, onChange: (Int) -> Unit) {
        column.addView(SeekBar(this).apply {
            this.max = max
            progress = value.coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
    }
}
