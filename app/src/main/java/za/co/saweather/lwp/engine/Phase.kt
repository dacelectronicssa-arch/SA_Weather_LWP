package za.co.saweather.lwp.engine

/** The ten phases of the shared 24-hour weather cycle, with their start hours. */
enum class Phase(val label: String, val start: Float) {
    LATE_NIGHT("Late night", 0f),
    DAWN("Dawn", 5f),
    MORNING("Morning", 7f),
    MIDDAY("Midday", 11f),
    STORM_BUILD("Storm building", 14f),
    STORM_PEAK("Storm", 15.5f),
    CLEARING("Clearing", 17f),
    GOLDEN_HOUR("Golden hour", 18f),
    DUSK("Dusk", 19f),
    NIGHT("Night", 20.5f);

    companion object {
        fun at(hour: Float): Phase {
            val h = ((hour % 24f) + 24f) % 24f
            return entries.last { it.start <= h }
        }
    }
}
