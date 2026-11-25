package com.kyuusanq3.aabooter

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts Gearhead's wireless projection activity. That activity is not exported on Gearhead 18.
 * This process runs as uid 1000, and the system allows that uid to start it.
 */
class GearheadLaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = intent.getStringExtra(EXTRA_HOST)
        val port = intent.getIntExtra(EXTRA_PORT, -1)
        if (host.isNullOrBlank() || port <= 0) {
            Log.w(TAG, "Launch ignored: host=$host port=$port")
            return
        }
        val launch = Intent(ACTION_START).apply {
            component = ComponentName(GEARHEAD, ACTIVITY)
            putExtra(EXTRA_HOST, host)
            putExtra(EXTRA_PORT, port)
            putExtra(EXTRA_HOST_ALT, host)
            putExtra(EXTRA_PORT_ALT, port)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(launch)
            if (isOrderedBroadcast) setResultCode(Activity.RESULT_OK)
            Log.i(TAG, "Started WirelessStartupActivity $host:$port")
        } catch (error: Exception) {
            Log.e(TAG, "WirelessStartupActivity was not started", error)
        }
    }

    companion object {
        const val TAG = "AaBooter"
        const val ACTION = "com.kyuusanq3.aabooter.LAUNCH_GEARHEAD"
        private const val GEARHEAD = "com.google.android.projection.gearhead"
        private const val ACTIVITY =
            "com.google.android.apps.auto.wireless.setup.service.impl.WirelessStartupActivity"
        private const val ACTION_START =
            "com.google.android.projection.gearhead.START_WIRELESS_PROJECTION"
        private const val EXTRA_HOST = "PARAM_HOST_ADDRESS"
        private const val EXTRA_PORT = "PARAM_SERVICE_PORT"
        private const val EXTRA_HOST_ALT = "ip_address"
        private const val EXTRA_PORT_ALT = "projection_port"
    }
}
