package com.kyuusanq3.aabooter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object HeadUnitServerStarter {
    const val TAG = "AaBooter"

    private const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"
    private const val HEAD_UNIT_SERVICE =
        "com.google.android.projection.gearhead.companion.DeveloperHeadUnitNetworkService"

    fun start(context: Context) {
        val component = ComponentName(GEARHEAD_PACKAGE, HEAD_UNIT_SERVICE)
        val intent = Intent().setComponent(component)
        try {
            context.startForegroundService(intent)
            Log.i(TAG, "startForegroundService OK component=$component")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting head unit server component=$component", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting head unit server component=$component", e)
        }
    }
}
