package za.co.saweather.lwp

import android.app.WallpaperManager
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import za.co.saweather.lwp.engine.SurfaceDriver

class WeatherWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WeatherEngine()

    inner class WeatherEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private lateinit var driver: SurfaceDriver
        private val prefs = SettingsStore.prefs(this@WeatherWallpaperService)
        private var settings = SettingsStore.load(this@WeatherWallpaperService)
        private val tilt = TiltSensor(this@WeatherWallpaperService) { x, y -> driver.setTilt(x, y) }
        private var lastTap = 0L
        private var shownScene = settings.scene

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(true)
            driver = SurfaceDriver(surfaceHolder, "sa-weather-wallpaper")
            driver.onSceneShown = { shownScene = it }
            driver.applySettings(settings)
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            tilt.stop()
            driver.release()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            settings = SettingsStore.load(this@WeatherWallpaperService)
            driver.applySettings(settings)
            if (isVisible) updateSensors(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            driver.setVisible(visible)
            updateSensors(visible)
        }

        private fun updateSensors(visible: Boolean) {
            if (visible && settings.parallaxActive) tilt.start() else tilt.stop()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            driver.surfaceChanged(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            driver.setVisible(false)
            driver.surfaceDestroyed()
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            driver.setLauncherOffset(xOffset)
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP && settings.doubleTapScene && !settings.rotateScenes) {
                val now = SystemClock.uptimeMillis()
                if (now - lastTap < 350L) {
                    val next = SceneId.entries[(shownScene.ordinal + 1) % SceneId.entries.size]
                    SettingsStore.setScene(this@WeatherWallpaperService, next)
                    lastTap = 0L
                } else {
                    lastTap = now
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }
    }
}
