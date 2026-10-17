package com.weatherwall.app.render

import android.graphics.Canvas

/** The land under the shared sky: Cape Town or the Savanna. */
interface Landscape {
    /** Screen y of the highest landmark (Cape Town: the top of the table); the storm base hangs near it. */
    val tableTopY: Float

    /** Vertical scale of the land's features, in pixels. */
    val mountainUnit: Float

    fun update(dt: Float)

    /** [onWater] draws the visitors that live in this landscape (boats, whales...); unused by the Savanna. */
    fun draw(canvas: Canvas, onWater: ((Canvas) -> Unit)? = null)

    fun setTwilight(amount: Float)
    fun setLight(direction: Float, intensity: Float) {}
    fun setGlint(x: Float, strength: Float) {}

    /** 0..1: a lightning flash lighting up the land. */
    fun setFlash(amount: Float) {}

    /** Free perches for pigeons, [gap] apart: one array of (x, y) pairs per roof or ledge. */
    fun roofSpots(gap: Float): List<FloatArray> = emptyList()
}

/** Which scene the wallpaper shows. */
object Scenes {
    const val CAPE_TOWN = "cape_town"
    const val SAVANNA = "savanna"
    const val JHB = "jhb_city"
    const val ALTRON = "altron_hq"
    const val GARDEN = "garden_route"

    /** Where the land meets the sky, as a fraction of screen height. */
    fun horizon(scene: String) = when (scene) {
        SAVANNA -> SavannaDesign.HORIZON
        JHB -> JhbDesign.HORIZON
        ALTRON -> AltronDesign.HORIZON
        GARDEN -> GardenDesign.HORIZON
        else -> 0.72f
    }
}
