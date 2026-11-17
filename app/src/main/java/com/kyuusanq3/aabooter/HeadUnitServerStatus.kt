package com.kyuusanq3.aabooter

import android.util.Log
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Android Auto's developer head unit server listens on TCP 5277.
 * Reading /proc/net/tcp avoids opening that port. A short connect is only
 * used when this process cannot read the proc listing.
 */
internal object HeadUnitServerStatus {
    const val PORT = 5277

    fun isListening(): Boolean {
        val tcp = readProc(File("/proc/net/tcp"))
        val tcp6 = readProc(File("/proc/net/tcp6"))
        if (tcp != null || tcp6 != null) {
            return (tcp != null && listensOnPort(tcp, PORT)) ||
                (tcp6 != null && listensOnPort(tcp6, PORT))
        }
        Log.i(HeadUnitServerStarter.TAG, "proc net unreadable; probing 127.0.0.1:$PORT")
        return tcpProbe()
    }
}

internal fun listensOnPort(procText: String, port: Int): Boolean {
    val suffix = ":%X".format(port)
    return procText.lineSequence().any { line ->
        val columns = line.trim().split(Regex("\\s+"))
        if (columns.size < 4) return@any false
        columns[1].endsWith(suffix, ignoreCase = true) &&
            columns[3].equals("0A", ignoreCase = true)
    }
}

private fun readProc(file: File): String? {
    return try {
        if (!file.canRead()) null else file.readText()
    } catch (_: Exception) {
        null
    }
}

private fun tcpProbe(): Boolean {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", HeadUnitServerStatus.PORT), 400)
        }
        true
    } catch (_: Exception) {
        false
    }
}
