package com.kyuusanq3.aabooter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

internal object HeadUnitServerStarter {
    const val TAG = "AaBooter"

    private const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"
    private const val HEAD_UNIT_SERVICE =
        "com.google.android.projection.gearhead.companion.DeveloperHeadUnitNetworkService"

    fun start(context: Context): Boolean {
        return run(context, start = true)
    }

    fun stop(context: Context): Boolean {
        return run(context, start = false)
    }

    private fun run(context: Context, start: Boolean): Boolean {
        val component = ComponentName(GEARHEAD_PACKAGE, HEAD_UNIT_SERVICE)
        val intent = Intent().setComponent(component)
        return try {
            if (start) {
                context.startForegroundService(intent)
                Log.i(TAG, "startForegroundService OK component=$component")
            } else {
                val stopped = context.stopService(intent)
                Log.i(TAG, "stopService result=$stopped component=$component")
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException ${if (start) "starting" else "stopping"} head unit server", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed ${if (start) "starting" else "stopping"} head unit server", e)
            false
        }
    }
}
