package com.kyuusanq3.aabooter

import android.content.Context
import android.net.wifi.SoftApConfiguration
import android.os.SystemClock
import android.util.Log
import android.util.SparseIntArray
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Stops the tethered hotspot and starts it again on 2.4 GHz.
 *
 * After a car boot the S10 restores the hotspot switch before the radio is
 * beaconing. The switch stays on and the T9 never sees the SSID. The settings
 * toggle is a stop plus a start of Wi-Fi tethering; this is that toggle.
 * Hidden framework calls are allowed because this process is uid 1000.
 */
internal object HotspotRestarter {
    private const val BAND_2GHZ = 1
    private const val STOP_SETTLE_MS = 2_000L
    private const val START_WAIT_S = 8L

    fun restart(context: Context): HotspotRestartResult = synchronized(this) {
        val app = context.applicationContext
        val startedAt = SystemClock.elapsedRealtime()
        val bandNote = lockTo24Ghz(app)
        val stopped = stopWifiTethering(app)
        if (!stopped.ok) {
            return stopped
        }
        sleepSettle()
        var started = startWifiTethering(app)
        if (!started.ok) {
            Log.w(HeadUnitServerStarter.TAG, "Hotspot start failed (${started.detail}); retrying")
            sleepSettle()
            started = startWifiTethering(app)
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val detail = if (started.ok) {
            "Hotspot restarted. $bandNote"
        } else {
            started.detail
        }
        Log.i(HeadUnitServerStarter.TAG, "$detail (${elapsed}ms)")
        return HotspotRestartResult(started.ok, detail)
    }

    private fun lockTo24Ghz(context: Context): String {
        return try {
            val wifi = context.getSystemService(Context.WIFI_SERVICE)
                ?: return "Wi-Fi service missing; band left unchanged."
            val current = wifi.javaClass.getMethod("getSoftApConfiguration").invoke(wifi)
                as? SoftApConfiguration
                ?: return "No saved hotspot; band left unchanged."
            val ssid = current.wifiSsid?.toString() ?: "(no SSID)"
            val builderClass = Class.forName("android.net.wifi.SoftApConfiguration\$Builder")
            val builder = builderClass.getConstructor(SoftApConfiguration::class.java)
                .newInstance(current)
            applyBand(builder, builderClass)
            invokeIfPresent(builder, builderClass, "setHiddenSsid", false)
            val updated = builderClass.getMethod("build").invoke(builder) as SoftApConfiguration
            val saved = wifi.javaClass
                .getMethod("setSoftApConfiguration", SoftApConfiguration::class.java)
                .invoke(wifi, updated) as Boolean
            val note = if (saved) {
                "Saved $ssid on 2.4 GHz."
            } else {
                "Could not save 2.4 GHz for $ssid."
            }
            Log.i(HeadUnitServerStarter.TAG, note)
            note
        } catch (error: Exception) {
            Log.e(HeadUnitServerStarter.TAG, "Could not lock hotspot to 2.4 GHz", unwrap(error))
            "Band left unchanged."
        }
    }

    private fun applyBand(builder: Any, builderClass: Class<*>) {
        try {
            builderClass.getMethod("setBand", Int::class.javaPrimitiveType)
                .invoke(builder, BAND_2GHZ)
            return
        } catch (_: NoSuchMethodException) {
            Log.i(HeadUnitServerStarter.TAG, "setBand missing; using setChannels")
        }
        val channels = SparseIntArray()
        channels.put(BAND_2GHZ, 0)
        builderClass.getMethod("setChannels", SparseIntArray::class.java)
            .invoke(builder, channels)
    }

    private fun stopWifiTethering(context: Context): HotspotRestartResult {
        return try {
            val tethering = tetheringService(context)
                ?: return HotspotRestartResult(false, "Tethering service is missing.")
            tethering.javaClass
                .getMethod("stopTethering", Int::class.javaPrimitiveType)
                .invoke(tethering, tetheringWifiType())
            Log.i(HeadUnitServerStarter.TAG, "Hotspot stop requested")
            HotspotRestartResult(true, "Hotspot stop requested.")
        } catch (error: Exception) {
            val cause = unwrap(error)
            Log.e(HeadUnitServerStarter.TAG, "Could not stop hotspot", cause)
            HotspotRestartResult(false, "Could not stop the hotspot.")
        }
    }

    private fun startWifiTethering(context: Context): HotspotRestartResult {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            val tethering = tetheringService(context)
                ?: return HotspotRestartResult(false, "Tethering service is missing.")
            val callbackClass = Class.forName(
                "android.net.TetheringManager\$StartTetheringCallback",
            )
            val latch = CountDownLatch(1)
            val started = AtomicReference(false)
            val detail = AtomicReference("Hotspot start did not answer.")
            val callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { proxy, method, args ->
                when (method.name) {
                    "onTetheringStarted" -> {
                        started.set(true)
                        detail.set("Hotspot is beaconing.")
                        latch.countDown()
                        null
                    }
                    "onTetheringFailed" -> {
                        started.set(false)
                        detail.set("Hotspot did not start (code ${args?.firstOrNull()}).")
                        latch.countDown()
                        null
                    }
                    "toString" -> "HotspotStartCallback"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }
            invokeStartTethering(tethering, callbackClass, executor, callback)
            if (!latch.await(START_WAIT_S, TimeUnit.SECONDS)) {
                logStartOverloads(tethering)
                HotspotRestartResult(false, "Hotspot start timed out.")
            } else {
                HotspotRestartResult(started.get(), detail.get())
            }
        } catch (error: Exception) {
            val cause = unwrap(error)
            Log.e(HeadUnitServerStarter.TAG, "Could not start hotspot", cause)
            HotspotRestartResult(false, "Could not start the hotspot.")
        } finally {
            executor.shutdownNow()
        }
    }

    private fun invokeStartTethering(
        tethering: Any,
        callbackClass: Class<*>,
        executor: Executor,
        callback: Any,
    ) {
        val request = silentRequest()
        if (request != null) {
            try {
                tethering.javaClass.getMethod(
                    "startTethering",
                    request.javaClass,
                    Executor::class.java,
                    callbackClass,
                ).invoke(tethering, request, executor, callback)
                return
            } catch (_: NoSuchMethodException) {
                Log.i(HeadUnitServerStarter.TAG, "TetheringRequest start is missing; using the type")
            }
        }
        tethering.javaClass.getMethod(
            "startTethering",
            Int::class.javaPrimitiveType,
            Executor::class.java,
            callbackClass,
        ).invoke(tethering, tetheringWifiType(), executor, callback)
    }

    private fun silentRequest(): Any? {
        return try {
            val builderClass = Class.forName(
                "android.net.TetheringManager\$TetheringRequest\$Builder",
            )
            val builder = builderClass
                .getConstructor(Int::class.javaPrimitiveType)
                .newInstance(tetheringWifiType())
            invokeIfPresent(builder, builderClass, "setExemptFromEntitlementCheck", true)
            invokeIfPresent(builder, builderClass, "setShouldShowEntitlementUi", false)
            builderClass.getMethod("build").invoke(builder)
        } catch (error: Exception) {
            Log.w(HeadUnitServerStarter.TAG, "Using the default tethering request", unwrap(error))
            null
        }
    }

    private fun tetheringService(context: Context): Any? {
        return context.getSystemService("tethering")
    }

    private fun tetheringWifiType(): Int {
        return try {
            Class.forName("android.net.TetheringManager")
                .getField("TETHERING_WIFI")
                .getInt(null)
        } catch (_: Exception) {
            0
        }
    }

    private fun invokeIfPresent(target: Any, type: Class<*>, name: String, arg: Boolean) {
        try {
            type.getMethod(name, Boolean::class.javaPrimitiveType).invoke(target, arg)
        } catch (_: NoSuchMethodException) {
            Log.i(HeadUnitServerStarter.TAG, "$name is not on this build")
        }
    }

    private fun logStartOverloads(tethering: Any) {
        val overloads = tethering.javaClass.methods
            .filter { it.name == "startTethering" }
            .joinToString { method ->
                method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }
            }
        Log.w(HeadUnitServerStarter.TAG, "startTethering overloads: $overloads")
    }

    private fun sleepSettle() {
        try {
            Thread.sleep(STOP_SETTLE_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun unwrap(error: Throwable): Throwable {
        return (error as? InvocationTargetException)?.cause ?: error
    }
}

internal data class HotspotRestartResult(val ok: Boolean, val detail: String)
