package com.kyuusanq3.aabooter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Waits until the Wi-Fi driver has had time to finish booting, then restarts
 * the hotspot. 90 seconds from power-on is past the S10's own radio startup.
 * If boot has already taken longer than that, the bounce runs a few seconds
 * after this broadcast.
 */
internal object HotspotBoot {
    const val ACTION = "com.kyuusanq3.aabooter.RESTART_HOTSPOT"
    private const val READY_AFTER_BOOT_MS = 90_000L
    private const val ALREADY_READY_DELAY_MS = 5_000L

    fun schedule(context: Context) {
        val app = context.applicationContext
        val elapsed = SystemClock.elapsedRealtime()
        val delay = if (elapsed >= READY_AFTER_BOOT_MS) {
            ALREADY_READY_DELAY_MS
        } else {
            READY_AFTER_BOOT_MS - elapsed
        }
        val trigger = elapsed + delay
        val alarm = app.getSystemService(AlarmManager::class.java)
        val pending = pendingRestart(app)
        try {
            alarm.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                trigger,
                pending,
            )
            Log.i(
                HeadUnitServerStarter.TAG,
                "Hotspot restart in ${delay}ms (elapsed ${elapsed}ms)",
            )
        } catch (error: SecurityException) {
            Log.e(HeadUnitServerStarter.TAG, "Exact alarm rejected; using inexact", error)
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
        }
    }

    private fun pendingRestart(context: Context): PendingIntent {
        val intent = Intent(context, HotspotRestartReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class HotspotRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != HotspotBoot.ACTION) return
        if (!BootSettings.isRestartHotspotOnBoot(context)) {
            Log.i(HeadUnitServerStarter.TAG, "Hotspot restart was turned off before the alarm")
            return
        }
        Log.i(HeadUnitServerStarter.TAG, "Hotspot restart alarm")
        val service = Intent(context, HotspotRestartService::class.java)
            .putExtra(
                HotspotRestartService.EXTRA_START_SERVER,
                BootSettings.isStartOnBoot(context),
            )
        try {
            context.startForegroundService(service)
        } catch (error: Exception) {
            Log.e(HeadUnitServerStarter.TAG, "Could not start hotspot restart", error)
        }
    }
}
