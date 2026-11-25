package com.kyuusanq3.aabooter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts Android Auto's head unit server on boot, and schedules the late
 * hotspot bounce. The bounce waits until the radio has finished starting.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(HeadUnitServerStarter.TAG, "BootCompletedReceiver action=${intent?.action}")
        val app = context.applicationContext
        if (BootSettings.isStartOnBoot(app)) {
            HeadUnitServerStarter.start(app)
        } else {
            Log.i(HeadUnitServerStarter.TAG, "Start on boot is off")
        }
        if (BootSettings.isRestartHotspotOnBoot(app)) {
            HotspotBoot.schedule(app)
        } else {
            Log.i(HeadUnitServerStarter.TAG, "Hotspot restart on boot is off")
        }
    }
}
