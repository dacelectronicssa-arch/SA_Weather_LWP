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
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.weatherwall.app.render.Holidays
import com.weatherwall.app.render.MoonPhase
import com.weatherwall.app.render.ScenePreview
import com.weatherwall.app.render.Scenes
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class SettingsActivity : Activity() {

    private val prefs by lazy { Prefs.get(this) }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_FETCHED_AT || key == Prefs.KEY_OVERRIDE || key == Prefs.KEY_PREVIEW_HOLIDAY || key == Prefs.KEY_CITY) updateStatus()
        if (key == Prefs.KEY_OVERRIDE) updatePreviewCard()
        if (key == Prefs.KEY_SCENE) updateScenePicker()
        if (key == Prefs.KEY_FETCHED_AT || key == Prefs.KEY_OVERRIDE || key == Prefs.KEY_SCENE) refreshPreviews()
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
            // `--ei fps 30|24|15` sets the top frame rate; `--ei fps 0` picks Ultra power saver.
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

        // Behind the status and navigation bars too (the app draws edge to edge on Android 15).
        window.decorView.setBackgroundColor(Ui.BG)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        // Title.
        root.addView(Ui.text(this, "Weather Wallpaper", 28f, Ui.TEXT, Ui.BOLD))
        root.addView(Ui.text(this, "Live South African skies", 14f, Ui.MUTED).apply { setPadding(0, dp(2), 0, dp(18)) })

        // Hero: the wallpaper as it looks right now, with the weather over it.
        root.addView(buildHero(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260)))
        root.addView(Ui.pillButton(this, "Set as wallpaper", filled = true) { openWallpaperPicker() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })

        root.addView(Ui.section(this, "Scene"))
        root.addView(buildScenePicker())

        root.addView(Ui.section(this, "Smoothness"))
        root.addView(buildSmoothnessCard())

        root.addView(Ui.section(this, "Location"))
        root.addView(buildLocationCard())

        root.addView(Ui.section(this, "Preview a condition"))
        root.addView(buildPreviewCard())

        root.addView(Ui.text(this, "Sky, sun, moon and stars follow your real time and place.", 12f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Ui.BG)
            isVerticalScrollBarEnabled = false
            addView(root)
        })
        updateStatus()
        refreshPreviews()
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

    // ---- Hero --------------------------------------------------------------------------------

    private lateinit var heroImage: ImageView
    private lateinit var heroTemp: TextView
    private lateinit var heroCondition: TextView
    private lateinit var heroPlace: TextView
    private lateinit var heroMoon: TextView
    private lateinit var heroBadge: TextView

    private fun buildHero(): View {
        val r = dp(24).toFloat()
        val frame = FrameLayout(this).apply {
            background = Ui.rounded(Ui.CARD, r)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }
        heroImage = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        frame.addView(heroImage, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // Darken the bottom so the text reads over any sky.
        frame.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x22000000, 0xCC05080F.toInt()))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        heroBadge = Ui.text(this, "", 12f, Ui.ON_ACCENT, Ui.MEDIUM).apply {
            background = Ui.rounded(Ui.ACCENT, dp(12).toFloat())
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        frame.addView(heroBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(14); topMargin = dp(14)
        })
        heroMoon = Ui.text(this, "", 12f, Ui.TEXT, Ui.MEDIUM).apply {
            background = Ui.rounded(0x8C0B111C.toInt(), dp(12).toFloat())
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        frame.addView(heroMoon, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            rightMargin = dp(14); topMargin = dp(14)
        })

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroTemp = Ui.text(this, "", 44f, Ui.TEXT, Typeface.create("sans-serif-light", Typeface.NORMAL))
        heroCondition = Ui.text(this, "", 17f, Ui.TEXT, Ui.MEDIUM)
        heroPlace = Ui.text(this, "", 13f, 0xFFC4CEDC.toInt())
        info.addView(heroTemp); info.addView(heroCondition); info.addView(heroPlace)
        frame.addView(info, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dp(18); bottomMargin = dp(16)
        })
        return frame
    }

    // ---- Scene picker --------------------------------------------------------------------------

    private class SceneOption(val id: String, val name: String, val place: String)

    private val sceneOptions = listOf(
        SceneOption(Scenes.CAPE_TOWN, "Cape Town", "Table Bay"),
        SceneOption(Scenes.SAVANNA, "Savanna", "The bushveld"),
        SceneOption(Scenes.JHB, "Jhb City", "Hillbrow skyline"),
        SceneOption(Scenes.ALTRON, "Altron", "Head office"),
    )
    private val sceneThumbs = HashMap<String, ImageView>()
    private val sceneFrames = HashMap<String, View>()

    private fun buildScenePicker(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (o in sceneOptions) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                setOnClickListener { prefs.edit().putString(Prefs.KEY_SCENE, o.id).apply(); updateScenePicker() }
            }
            val frame = FrameLayout(this).apply {
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                setPadding(dp(3), dp(3), dp(3), dp(3))
            }
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = Ui.rounded(Ui.RAISED, dp(13).toFloat())
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
            }
            frame.addView(thumb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            tile.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)))
            tile.addView(Ui.text(this, o.name, 14f, Ui.TEXT, Ui.MEDIUM).apply { setPadding(dp(4), dp(8), 0, 0) })
            tile.addView(Ui.text(this, o.place, 12f, Ui.MUTED).apply { setPadding(dp(4), 0, 0, 0) })
            sceneThumbs[o.id] = thumb
            sceneFrames[o.id] = frame
            row.addView(tile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (o !== sceneOptions.last()) rightMargin = dp(10)
            })
        }
        updateScenePicker()
        return row
    }

    private fun updateScenePicker() {
        val current = Prefs.scene(this)
        for ((id, frame) in sceneFrames) {
            val on = id == current
            frame.background = Ui.rounded(Color.TRANSPARENT, dp(16).toFloat(), dp(if (on) 2 else 1), if (on) Ui.ACCENT else Ui.LINE)
        }
    }

    // ---- Smoothness ----------------------------------------------------------------------------

    private val fpsValues = listOf(30, 24, 15, Prefs.ULTRA_FPS)
    private val fpsNotes = listOf(
        "Smoothest motion. Uses the most battery.",
        "Smooth and easy on the battery. Recommended.",
        "Gentler on the battery; motion is a little choppier.",
        "A still picture that updates every minute and comes alive for a few seconds when you unlock.",
    )

    private fun buildSmoothnessCard(): View {
        val card = Ui.card(this)
        val note = Ui.text(this, "", 13f, Ui.MUTED).apply { setPadding(dp(2), dp(12), 0, 0) }
        val seg = Segmented(this, listOf("30 fps", "24 fps", "15 fps", "Ultra")) { i ->
            prefs.edit().putInt(Prefs.KEY_MAX_FPS, fpsValues[i]).apply()
            note.text = fpsNotes[i]
        }
        val i = fpsValues.indexOf(Prefs.maxFps(this)).coerceAtLeast(0)
        seg.select(i)
        note.text = fpsNotes[i]
        card.addView(seg)
        card.addView(note)
        card.addView(Ui.divider(this))
        card.addView(switchRow(
            "Ultra with Battery Saver",
            "Switch to Ultra automatically while the phone's Battery Saver is on.",
            Prefs.ultraOnBatterySaver(this),
        ) { on -> prefs.edit().putBoolean(Prefs.KEY_ULTRA_ON_BATTERY_SAVER, on).apply() })
        return card
    }

    private fun switchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(Ui.text(this, title, 15f, Ui.TEXT, Ui.MEDIUM))
        texts.addView(Ui.text(this, detail, 13f, Ui.MUTED).apply { setPadding(0, dp(2), dp(12), 0) })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        row.addView(Switch(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList(states, intArrayOf(Ui.ACCENT, 0xFFB8C2D0.toInt()))
            trackTintList = ColorStateList(states, intArrayOf(0x805CC8F2.toInt(), Ui.LINE))
            setOnCheckedChangeListener { _, on -> onChange(on) }
        })
        return row
    }

    // ---- Location ------------------------------------------------------------------------------

    private lateinit var locationNote: TextView

    private fun buildLocationCard(): View {
        val card = Ui.card(this)
        locationNote = Ui.text(this, "", 14f, Ui.TEXT)
        card.addView(locationNote)
        val city = EditText(this).apply {
            hint = "Type a city, or leave empty for GPS"
            isSingleLine = true
            setText(prefs.getString(Prefs.KEY_CITY, ""))
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = Ui.rounded(Ui.RAISED, dp(14).toFloat(), dp(1), Ui.LINE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.addView(city, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Ui.pillButton(this, "Save city", filled = true) {
            prefs.edit().putString(Prefs.KEY_CITY, city.text.toString().trim()).apply()
            updateStatus()
            refreshNow()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(10) })
        buttons.addView(Ui.pillButton(this, "Use my location", filled = false) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        card.addView(Ui.text(this, "Refresh weather now", 14f, Ui.ACCENT, Ui.MEDIUM).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(2))
            isClickable = true
            setOnClickListener { refreshNow() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return card
    }

    // ---- Preview a condition -------------------------------------------------------------------

    private val conditionChips = LinkedHashMap<String, TextView>() // value -> chip ("auto", "FIREWORKS", condition names)
    private lateinit var dayNight: Segmented

    private fun buildPreviewCard(): View {
        val card = Ui.card(this)
        card.addView(Ui.text(this, "Try the wallpaper in any weather. Live weather returns to the real sky.", 13f, Ui.MUTED).apply {
            setPadding(dp(2), 0, 0, dp(12))
        })
        val flow = FlowLayout(this, dp(8), dp(8))
        fun add(value: String, label: String) {
            val chip = Ui.chip(this, label) { choosePreview(value) }
            conditionChips[value] = chip
            flow.addView(chip)
        }
        add(Prefs.OVERRIDE_AUTO, "Live weather")
        for (c in WeatherCondition.entries) add(c.name, c.label)
        add(Prefs.OVERRIDE_FIREWORKS, "Fireworks")
        card.addView(flow)
        dayNight = Segmented(this, listOf("Day", "Night")) { _ ->
            val cur = currentPreviewCondition()
            if (cur != null) choosePreview(cur)
        }
        card.addView(dayNight, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        updatePreviewCard()
        return card
    }

    /** The previewed condition's name, or null for live weather / fireworks. */
    private fun currentPreviewCondition(): String? {
        val v = prefs.getString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO) ?: Prefs.OVERRIDE_AUTO
        if (v == Prefs.OVERRIDE_AUTO || v == Prefs.OVERRIDE_FIREWORKS) return null
        return v.substringBefore(':')
    }

    private fun choosePreview(value: String) {
        val e = prefs.edit()
        when (value) {
            Prefs.OVERRIDE_AUTO -> e.putString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO)
            // Fireworks: a different random holiday each time it's picked.
            Prefs.OVERRIDE_FIREWORKS -> e.putString(Prefs.KEY_PREVIEW_HOLIDAY, Holidays.ALL.random().name).putString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_FIREWORKS)
            else -> {
                val night = dayNight.selected == 1
                e.putString(Prefs.KEY_OVERRIDE, Prefs.overrideValue(WeatherCondition.valueOf(value), night))
            }
        }
        e.apply()
        updatePreviewCard()
    }

    private fun updatePreviewCard() {
        val v = prefs.getString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO) ?: Prefs.OVERRIDE_AUTO
        val selected = when (v) {
            Prefs.OVERRIDE_AUTO, Prefs.OVERRIDE_FIREWORKS -> v
            else -> v.substringBefore(':')
        }
        for ((value, chip) in conditionChips) Ui.setChipSelected(chip, value == selected)
        val conditionChosen = selected != Prefs.OVERRIDE_AUTO && selected != Prefs.OVERRIDE_FIREWORKS
        dayNight.select(if (v.endsWith(":night") || (!conditionChosen && dayNight.selected == 1)) 1 else 0)
        dayNight.isEnabled = conditionChosen
    }

    // ---- Live previews -------------------------------------------------------------------------

    private val previewWorker = Executors.newSingleThreadExecutor()
    private var previewGeneration = 0

    /** Re-renders the hero and the scene thumbnails with the current weather (off the main thread). */
    private fun refreshPreviews() {
        val gen = ++previewGeneration
        val weather = Prefs.effectiveWeather(this)
        val lat = Prefs.latitude(this)
        val lon = Prefs.longitude(this)
        val scene = Prefs.scene(this)
        val density = resources.displayMetrics.density
        // The hero card's shape (full width less the margins, 260dp tall), to crop to.
        val heroAspect = dp(260).toFloat() / (resources.displayMetrics.widthPixels - dp(40)).coerceAtLeast(1)
        previewWorker.execute {
            // Hero: a portrait frame cropped around the landmark.
            val w = 540
            val h = (w * 2.17f).toInt()
            val full = ScenePreview.render(weather, lat, lon, scene, w, h, density * w / resources.displayMetrics.widthPixels.coerceAtLeast(1))
            val cropH = (w * heroAspect).toInt().coerceIn(1, h)
            val top = (h * ScenePreview.focus(scene) - cropH * 0.55f).toInt().coerceIn(0, h - cropH)
            val hero = Bitmap.createBitmap(full, 0, top, w, cropH)
            runOnUiThread { if (gen == previewGeneration && !isDestroyed) heroImage.setImageBitmap(hero) }
            for (o in sceneOptions) {
                val tw = 220
                val th = (tw * 2.17f).toInt()
                val thumb = ScenePreview.render(weather, lat, lon, o.id, tw, th, density * tw / resources.displayMetrics.widthPixels.coerceAtLeast(1))
                val ch = (tw * 1.5f).toInt()
                val tt = (th * ScenePreview.focus(o.id) - ch * 0.62f).toInt().coerceIn(0, th - ch)
                val crop = Bitmap.createBitmap(thumb, 0, tt, tw, ch)
                runOnUiThread { if (gen == previewGeneration && !isDestroyed) sceneThumbs[o.id]?.setImageBitmap(crop) }
            }
        }
    }

    override fun onDestroy() {
        previewWorker.shutdownNow()
        super.onDestroy()
    }

    private fun refreshNow() {
        heroPlace.text = "Fetching weather…"
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
        if (live == null) {
            heroTemp.text = "--°"
            heroCondition.text = "No weather yet"
            heroPlace.text = "Allow location or enter a city below"
        } else {
            heroTemp.text = if (live.temperatureC.isNaN()) "--°" else "${live.temperatureC.roundToInt()}°"
            heroCondition.text = live.condition.label
            val place = live.place.ifEmpty { "Your location" }
            heroPlace.text = "$place · updated ${DateFormat.getTimeFormat(this).format(Date(live.fetchedAt))}"
        }
        val phase = MoonPhase.phase()
        heroMoon.text = "${MoonPhase.name(phase)} · ${(MoonPhase.illumination(phase) * 100).roundToInt()}%"
        val override = prefs.getString(Prefs.KEY_OVERRIDE, Prefs.OVERRIDE_AUTO) ?: Prefs.OVERRIDE_AUTO
        heroBadge.visibility = if (override == Prefs.OVERRIDE_AUTO) View.GONE else View.VISIBLE
        heroBadge.text = when (override) {
            Prefs.OVERRIDE_FIREWORKS -> "Previewing: ${prefs.getString(Prefs.KEY_PREVIEW_HOLIDAY, "")} fireworks"
            else -> {
                val c = runCatching { WeatherCondition.valueOf(override.substringBefore(':')) }.getOrNull()
                "Previewing: ${c?.label ?: override} · ${if (override.endsWith(":night")) "night" else "day"}"
            }
        }
        val city = prefs.getString(Prefs.KEY_CITY, "")?.trim().orEmpty()
        locationNote.text = if (city.isEmpty()) "Weather for your location (GPS)" else "Weather for $city"
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun dp(v: Int) = Ui.dp(this, v.toFloat())

    private companion object {
        const val REQ_LOCATION = 1
    }
}
