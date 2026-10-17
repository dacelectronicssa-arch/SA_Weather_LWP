package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.weatherwall.app.WeatherState

/** Still pictures of a scene for the settings screen, drawn by the wallpaper's own renderer. */
object ScenePreview {
    /** Where each scene's landmark sits, as a fraction of a portrait frame's height (for cropping). */
    fun focus(scene: String) = when (scene) {
        Scenes.SAVANNA -> 0.62f
        Scenes.JHB -> 0.52f
        Scenes.ALTRON -> 0.7f
        else -> 0.62f
    }

    /**
     * The scene as it looks now under [weather], in a portrait frame of [width] × [height] px.
     * Runs a moment of animation first so clouds and stars settle. Safe off the main thread
     * (each call has its own renderer).
     */
    fun render(weather: WeatherState, latitude: Double, longitude: Double, scene: String, width: Int, height: Int, density: Float): Bitmap {
        val r = WeatherRenderer(density)
        r.setSize(width, height)
        r.setConfig(SceneConfig.forWeather(weather, latitude, longitude, scene), false)
        repeat(30) { r.update(1f / 24f) }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(bmp))
        return bmp
    }
}
