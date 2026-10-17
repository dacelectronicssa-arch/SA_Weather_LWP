package com.weatherwall.app

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.weatherwall.app.render.Holidays
import com.weatherwall.app.render.MoonPhase
import com.weatherwall.app.render.Scenes
import java.util.Date
import kotlin.math.roundToInt

class SettingsActivity : Activity() {

    private val prefs by lazy { Prefs.get(this) }
    private lateinit var status: TextView

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_FETCHED_AT || key == Prefs.KEY_OVERRIDE || key == Prefs.KEY_PREVIEW_HOLIDAY) updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug builds only: lets adb force a scene, e.g. `am start ... --es override CLEAR:night`.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            intent.getStringExtra("override")?.let {
                val e = prefs.edit().putString(Prefs.KEY_OVERRIDE, it)
                if (it == Prefs.OVERRIDE_FIREWORKS) e.putString(Prefs.KEY_PREVIEW_HOLIDAY, Holidays.ALL.random().name)
                e.apply()
            }
            // `--es sea tail|breach|dolphins` starts that whale/dolphin event now.
            intent.getStringExtra("sea")?.let {
                prefs.edit().putString(Prefs.KEY_DEBUG_SEA, "$it:${System.currentTimeMillis()}").apply()
            }
            // `--es boat yacht|jetski|container` sends that boat across now.
            intent.getStringExtra("boat")?.let {
                prefs.edit().putString(Prefs.KEY_DEBUG_BOAT, "$it:${System.currentTimeMillis()}").apply()
            }
            // `--ez birds true` sends a flock of hadedas over now.
            if (intent.getBooleanExtra("birds", false)) {
                prefs.edit().putLong(Prefs.KEY_DEBUG_BIRDS, System.currentTimeMillis()).apply()
            }
            // `--ez ship true` sends the cruise ship past now.
            if (intent.getBooleanExtra("ship", false)) {
                prefs.edit().putLong(Prefs.KEY_DEBUG_SHIP, System.currentTimeMillis()).apply()
            }
            // `--ei fps 30|24|15` sets the top frame rate.
            if (intent.hasExtra("fps")) prefs.edit().putInt(Prefs.KEY_MAX_FPS, intent.getIntExtra("fps", Prefs.DEFAULT_MAX_FPS)).apply()
            // `--es scene cape_town|savanna|jhb_city|altron_hq` switches the landscape.
            intent.getStringExtra("scene")?.let { prefs.edit().putString(Prefs.KEY_SCENE, it).apply() }
            // `--ez geese true` sends a skein of geese over the savanna now.
            if (intent.getBooleanExtra("geese", false)) {
                prefs.edit().putLong(Prefs.KEY_DEBUG_GEESE, System.currentTimeMillis()).apply()
            }
            // `--ez flypast true` sends the Airlink jets over Cape Town Stadium now.
            if (intent.getBooleanExtra("flypast", false)) {
                prefs.edit().putLong(Prefs.KEY_DEBUG_FLYPAST, System.currentTimeMillis()).apply()
            }
            // `--ez pigeons true` sends a flock of pigeons over Jhb now.
            if (intent.getBooleanExtra("pigeons", false)) {
                prefs.edit().putLong(Prefs.KEY_DEBUG_PIGEONS, System.currentTimeMillis()).apply()
            }
            // `--ez jet true` sends a 737 over Jhb now.
            if (intent.getBooleanExtra("jet", false)) {
                prefs.edit().putLong(Prefs.KEY_DEBUG_JET, System.currentTimeMillis()).apply()
            }
            // `--ez lions true` brings the lions onto the savanna now.
            if (intent.getBooleanExtra("lions", false)) {
                prefs.edit().putLong(Prefs.KEY_DEBUG_LIONS, System.currentTimeMillis()).apply()
            }
            // `--ez plane true` sends the banner plane over now.
            if (intent.getBooleanExtra("plane", false)) {
                prefs.edit().putLong(Prefs.KEY_DEBUG_PLANE, System.currentTimeMillis()).apply()
            }
            // e.g. `--ef time_offset_h 16` to preview the sky 16 hours from now.
            if (intent.hasExtra("time_offset_h")) {
                prefs.edit().putFloat(Prefs.KEY_DEBUG_TIME_OFFSET, intent.getFloatExtra("time_offset_h", 0f)).apply()
            }
        }

        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        })
        status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)

        root.addView(button("Set as wallpaper") { openWallpaperPicker() })

        root.addView(header("Scene"))
        root.addView(buildSceneSpinner())

        root.addView(header("Smoothness"))
        root.addView(buildFpsSpinner())

        root.addView(header("Location"))
        root.addView(button("Use my location") {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        })
        val city = EditText(this).apply {
            hint = "…or type a city (leave empty for GPS)"
            isSingleLine = true
            setText(prefs.getString(Prefs.KEY_CITY, ""))
        }
        root.addView(city)
        root.addView(button("Save city") {
            prefs.edit().putString(Prefs.KEY_CITY, city.text.toString().trim()).apply()
            refreshNow()
        })

        root.addView(header("Preview a condition"))
        root.addView(buildOverrideSpinner())

        root.addView(header("Weather"))
        root.addView(button("Refresh weather now") { refreshNow() })

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
        updateStatus()
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        updateStatus()
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCATION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (!prefs.getString(Prefs.KEY_CITY, "").isNullOrBlank()) {
                toast("Location allowed — clear the city field to use GPS instead of the city.")
            }
            refreshNow()
        } else {
            toast("Without location, enter a city instead.")
        }
    }

    private fun buildFpsSpinner(): Spinner {
        val labels = listOf("30 fps — smoothest", "24 fps — balanced", "15 fps — battery saver")
        val values = listOf(30, 24, 15)
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(values.indexOf(Prefs.maxFps(this@SettingsActivity)).coerceAtLeast(0), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (Prefs.maxFps(this@SettingsActivity) != values[position]) {
                        prefs.edit().putInt(Prefs.KEY_MAX_FPS, values[position]).apply()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun buildSceneSpinner(): Spinner {
        val labels = listOf("Cape Town — Table Bay", "Savanna — the bushveld", "Jhb City — Hillbrow skyline", "Altron — head office")
        val values = listOf(Scenes.CAPE_TOWN, Scenes.SAVANNA, Scenes.JHB, Scenes.ALTRON)
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(values.indexOf(Prefs.scene(this@SettingsActivity)).coerceAtLeast(0), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (Prefs.scene(this@SettingsActivity) != values[position]) {
                        prefs.edit().putString(Prefs.KEY_SCENE, values[position]).apply()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun buildOverrideSpinner(): Spinner {
        val labels = mutableListOf("Live weather (automatic)")
        val values = mutableListOf(Prefs.OVERRIDE_AUTO)
        for (c in WeatherCondition.entries) {
            labels += "${c.label} — day"; values += Prefs.overrideValue(c, night = false)
            labels += "${c.label} — night"; values += Prefs.overrideValue(c, night = true)
        }
        labels += "Fireworks — random holiday (night)"; values += Prefs.OVERRIDE_FIREWORKS
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            val saved = prefs.getString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO)
            setSelection(values.indexOf(saved).coerceAtLeast(0), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (prefs.getString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO) != values[position]) {
                        val e = prefs.edit()
                        // Fireworks: a different random holiday each time it's picked.
                        if (values[position] == Prefs.OVERRIDE_FIREWORKS) e.putString(Prefs.KEY_PREVIEW_HOLIDAY, Holidays.ALL.random().name)
                        e.putString(Prefs.KEY_OVERRIDE, values[position]).apply()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun refreshNow() {
        status.text = "Fetching weather…"
        Thread {
            val result = WeatherRepository(this).refresh()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onFailure { toast(it.message ?: "Couldn't fetch the weather.") }
                updateStatus()
            }
        }.start()
    }

    private fun updateStatus() {
        val live = Prefs.loadWeather(this)
        val text = StringBuilder()
        if (live == null) {
            text.append("No weather yet. Allow location or enter a city, then refresh.")
        } else {
            text.append("Now: ${live.condition.label}")
            if (!live.temperatureC.isNaN()) text.append(", ${live.temperatureC.roundToInt()}°C")
            text.append(if (live.isDay) " (day)" else " (night)")
            if (live.place.isNotEmpty()) text.append(" · ${live.place}")
            text.append("\nUpdated ${DateFormat.getTimeFormat(this).format(Date(live.fetchedAt))}")
        }
        val phase = MoonPhase.phase()
        text.append("\nMoon: ${MoonPhase.name(phase)}, ${(MoonPhase.illumination(phase) * 100).roundToInt()}% lit")
        if (prefs.getString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO) != Prefs.OVERRIDE_AUTO) {
            if (prefs.getString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO) == Prefs.OVERRIDE_FIREWORKS) {
                text.append("\nPreviewing fireworks: ${prefs.getString(Prefs.KEY_PREVIEW_HOLIDAY, "")}")
            }
            text.append("\nPreview override is on — the wallpaper ignores live weather.")
        }
        status.text = text
    }

    private fun openWallpaperPicker() {
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, WeatherWallpaperService::class.java),
        )
        try {
            startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: ActivityNotFoundException) {
                toast("This device doesn't offer a live wallpaper picker.")
            }
        }
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setPadding(0, dp(24), 0, dp(4))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val REQ_LOCATION = 1
    }
}
