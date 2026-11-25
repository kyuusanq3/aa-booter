package com.kyuusanq3.aabooter

import android.content.Context

internal object BootSettings {
    private const val PREFS = "aa_booter"
    private const val START_ON_BOOT = "start_on_boot"
    private const val RESTART_HOTSPOT_ON_BOOT = "restart_hotspot_on_boot"

    fun isStartOnBoot(context: Context): Boolean {
        return prefs(context).getBoolean(START_ON_BOOT, true)
    }

    fun setStartOnBoot(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(START_ON_BOOT, enabled).apply()
    }

    fun isRestartHotspotOnBoot(context: Context): Boolean {
        return prefs(context).getBoolean(RESTART_HOTSPOT_ON_BOOT, true)
    }

    fun setRestartHotspotOnBoot(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(RESTART_HOTSPOT_ON_BOOT, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
