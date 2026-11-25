package com.kyuusanq3.aabooter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs the hotspot bounce off the boot broadcast. The alarm only starts this
 * service; the stop, settle, and start need longer than a receiver is allowed
 * to run. The notification is removed when the bounce finishes.
 */
class HotspotRestartService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        val startServer = intent?.getBooleanExtra(EXTRA_START_SERVER, false) == true
        val generation = NEXT.incrementAndGet()
        val app = applicationContext
        worker.execute {
            val lock = wakeLock(app)
            lock.acquire(WAKE_MS)
            try {
                if (BootSettings.isRestartHotspotOnBoot(app)) {
                    val result = HotspotRestarter.restart(app)
                    Log.i(HeadUnitServerStarter.TAG, "Boot hotspot restart: ${result.detail}")
                }
                if (startServer && BootSettings.isStartOnBoot(app)) {
                    HeadUnitServerStarter.start(app)
                    Log.i(HeadUnitServerStarter.TAG, "Head unit server started after hotspot restart")
                }
            } finally {
                if (lock.isHeld) lock.release()
                if (generation == NEXT.get()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.hotspot_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.hotspot_restarting))
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_START_SERVER = "start_server"
        private const val CHANNEL = "hotspot_restart"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_MS = 30_000L
        private val NEXT = AtomicInteger()
        private val worker = Executors.newSingleThreadExecutor()

        private fun wakeLock(context: Context): PowerManager.WakeLock {
            return (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AaBooter:hotspot")
                .apply { setReferenceCounted(false) }
        }
    }
}
