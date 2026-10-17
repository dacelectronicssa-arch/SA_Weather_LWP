package com.weatherwall.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Debug builds only: lets adb drive the wallpaper without opening any screen, e.g. for a demo
 * recording:
 *   adb shell am broadcast -n com.weatherwall.app/.DemoReceiver --ez demo true
 * Also takes --es scene, --es override and --ef time_offset_h like the settings screen.
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val e = Prefs.get(context).edit()
        intent.getStringExtra("scene")?.let { e.putString(Prefs.KEY_SCENE, it) }
        intent.getStringExtra("override")?.let { e.putString(Prefs.KEY_OVERRIDE, it) }
        if (intent.hasExtra("time_offset_h")) e.putFloat(Prefs.KEY_DEBUG_TIME_OFFSET, intent.getFloatExtra("time_offset_h", 0f))
        // `--es fireworks "Heritage Day"` (any holiday name), `finale` for New Year's midnight, `off` to stop.
        intent.getStringExtra("fireworks")?.let { e.putString(Prefs.KEY_DEBUG_FIREWORKS, it) }
        if (intent.getBooleanExtra("demo", false)) e.putLong(Prefs.KEY_DEBUG_DEMO, System.currentTimeMillis())
        e.apply()
    }
}
