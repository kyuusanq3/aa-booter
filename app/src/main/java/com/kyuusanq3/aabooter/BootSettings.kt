package com.kyuusanq3.aabooter

import android.content.Context

internal object BootSettings {
    private const val PREFS = "aa_booter"
    private const val START_ON_BOOT = "start_on_boot"

    fun isStartOnBoot(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(START_ON_BOOT, true)
    }

    fun setStartOnBoot(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(START_ON_BOOT, enabled)
            .apply()
    }
}
