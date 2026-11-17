package com.kyuusanq3.aabooter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts Android Auto's head unit server on boot, then returns.
 * Does not keep a process running beyond the start call.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(HeadUnitServerStarter.TAG, "BootCompletedReceiver action=${intent?.action}")
        HeadUnitServerStarter.start(context.applicationContext)
    }
}
