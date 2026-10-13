package za.co.saweather.lwp

import android.content.Context
import android.content.SharedPreferences

enum class SceneId(val title: String, val subtitle: String) {
    HIGHVELD("Highveld Thunderstorm", "Gauteng Plateau"),
    CAPE_TOWN("Cape Town Atlantic Seaboard", "Table Mountain"),
    BUSHVELD("Kruger Bushveld", "Lowveld"),
    FOREST("Garden Route Forest", "Tsitsikamma / Knysna"),
    DRAKENSBERG("Drakensberg Mountains", "uKhahlamba"),
}

enum class ThemeId(val title: String) {
    AUTHENTIC("Authentic"),
    GOLDEN_AFRICA("Golden Africa"),
    MOODY_CAPE("Moody Cape"),
    VIVID("Vivid"),
    NOIR("Noir"),
    INFRARED("Infrared"),
    SUNSET_ALWAYS("Sunset Always"),
    DARK_MODE("Dark Mode"),
}

enum class TimeMode(val title: String) { REAL("Real device time"), DEMO("Demo mode"), MANUAL("Manual") }

enum class Performance(val title: String, val fps: Int, val particleScale: Float) {
    HIGH("High", 60, 1f),
    BALANCED("Balanced", 30, 0.7f),
    BATTERY("Battery saver", 20, 0.35f),
}

data class WallpaperSettings(
    val scene: SceneId = SceneId.CAPE_TOWN,
    val rotateScenes: Boolean = false,
    val theme: ThemeId = ThemeId.AUTHENTIC,
    val timeMode: TimeMode = TimeMode.REAL,
    val demoSpeed: Float = 60f,
    val manualHour: Float = 18.5f,
    val realLunar: Boolean = true,
    val milkyWay: Boolean = true,
    val shootingStars: Boolean = true,
    val constellations: Boolean = false,
    val planets: Boolean = false,
    val moonGlow: Boolean = true,
    val showAnimals: Boolean = true,
    val stormBehaviour: Boolean = true,
    val allAnimalsAlways: Boolean = false,
    val performance: Performance = Performance.BALANCED,
    val parallax: Boolean = true,
    val showTime: Boolean = false,
    val showSceneName: Boolean = false,
    val doubleTapScene: Boolean = true,
) {
    /** Parallax is disabled entirely in battery saver, per the brief. */
    val parallaxActive: Boolean get() = parallax && performance != Performance.BATTERY
}

object SettingsStore {
    const val PREFS = "sa_weather_lwp"

    const val SCENE = "scene"
    const val ROTATE = "rotate_scenes"
    const val THEME = "theme"
    const val TIME_MODE = "time_mode"
    const val DEMO_SPEED = "demo_speed"
    const val MANUAL_HOUR = "manual_hour"
    const val REAL_LUNAR = "real_lunar"
    const val MILKY_WAY = "milky_way"
    const val SHOOTING = "shooting_stars"
    const val CONSTELLATIONS = "constellations"
    const val PLANETS = "planets"
    const val MOON_GLOW = "moon_glow"
    const val ANIMALS = "animals"
    const val STORM_BEHAVIOUR = "storm_behaviour"
    const val ALL_ANIMALS = "all_animals"
    const val PERFORMANCE = "performance"
    const val PARALLAX = "parallax"
    const val SHOW_TIME = "show_time"
    const val SHOW_NAME = "show_scene_name"
    const val DOUBLE_TAP = "double_tap_scene"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): WallpaperSettings {
        val p = prefs(context)
        val d = WallpaperSettings()
        return WallpaperSettings(
            scene = enumOr(p.getString(SCENE, null), d.scene),
            rotateScenes = p.getBoolean(ROTATE, d.rotateScenes),
            theme = enumOr(p.getString(THEME, null), d.theme),
            timeMode = enumOr(p.getString(TIME_MODE, null), d.timeMode),
            demoSpeed = p.getFloat(DEMO_SPEED, d.demoSpeed),
            manualHour = p.getFloat(MANUAL_HOUR, d.manualHour),
            realLunar = p.getBoolean(REAL_LUNAR, d.realLunar),
            milkyWay = p.getBoolean(MILKY_WAY, d.milkyWay),
            shootingStars = p.getBoolean(SHOOTING, d.shootingStars),
            constellations = p.getBoolean(CONSTELLATIONS, d.constellations),
            planets = p.getBoolean(PLANETS, d.planets),
            moonGlow = p.getBoolean(MOON_GLOW, d.moonGlow),
            showAnimals = p.getBoolean(ANIMALS, d.showAnimals),
            stormBehaviour = p.getBoolean(STORM_BEHAVIOUR, d.stormBehaviour),
            allAnimalsAlways = p.getBoolean(ALL_ANIMALS, d.allAnimalsAlways),
            performance = enumOr(p.getString(PERFORMANCE, null), d.performance),
            parallax = p.getBoolean(PARALLAX, d.parallax),
            showTime = p.getBoolean(SHOW_TIME, d.showTime),
            showSceneName = p.getBoolean(SHOW_NAME, d.showSceneName),
            doubleTapScene = p.getBoolean(DOUBLE_TAP, d.doubleTapScene),
        )
    }

    fun setScene(context: Context, scene: SceneId) {
        prefs(context).edit().putString(SCENE, scene.name).apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
}
