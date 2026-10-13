package za.co.saweather.lwp.engine

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.SurfaceHolder
import za.co.saweather.lwp.WallpaperSettings

/**
 * Runs the [Renderer] on a dedicated thread against a SurfaceHolder, at the frame rate chosen in
 * the performance settings. Used by both the live wallpaper engine and the in-app preview.
 */
class SurfaceDriver(private val holder: SurfaceHolder, name: String) {
    private val thread = HandlerThread(name).apply { start() }
    private val handler = Handler(thread.looper)
    private val renderer = Renderer()
    @Volatile private var running = false
    @Volatile private var hasSurface = false

    /** Called after each frame with the scene that was drawn (on the render thread). */
    var onSceneShown: ((za.co.saweather.lwp.SceneId) -> Unit)? = null

    private val frame = object : Runnable {
        override fun run() {
            if (!running || !hasSurface) return
            val start = SystemClock.uptimeMillis()
            drawFrame()
            val budget = 1000L / renderer.settings.performance.fps
            val next = (budget - (SystemClock.uptimeMillis() - start)).coerceAtLeast(1L)
            handler.removeCallbacks(this)
            handler.postDelayed(this, next)
        }
    }

    private fun drawFrame() {
        val canvas = try {
            if (android.os.Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            renderer.render(canvas, System.nanoTime())
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
        }
        onSceneShown?.invoke(renderer.currentScene)
    }

    fun applySettings(s: WallpaperSettings) = handler.post { renderer.applySettings(s) }

    fun surfaceChanged(w: Int, h: Int) = handler.post {
        hasSurface = true
        renderer.setSize(w, h)
        if (running) { handler.removeCallbacks(frame); handler.post(frame) } else drawFrame()
    }

    fun surfaceDestroyed() {
        hasSurface = false
        handler.removeCallbacks(frame)
        // Make sure no frame is in flight against a dead surface.
        val done = java.util.concurrent.CountDownLatch(1)
        handler.post { done.countDown() }
        done.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    fun setVisible(visible: Boolean) = handler.post {
        running = visible
        handler.removeCallbacks(frame)
        if (visible) handler.post(frame)
    }

    fun setLauncherOffset(x: Float) = handler.post { renderer.setLauncherOffset(x) }
    fun setTilt(x: Float, y: Float) = handler.post { renderer.setTilt(x, y) }

    fun release() {
        running = false
        handler.removeCallbacksAndMessages(null)
        handler.post { renderer.release() }
        thread.quitSafely()
    }
}
