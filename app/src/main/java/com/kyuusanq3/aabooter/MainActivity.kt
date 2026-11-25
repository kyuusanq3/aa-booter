package com.kyuusanq3.aabooter

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updates: AppUpdateClient

    private lateinit var serverStatus: TextView
    private lateinit var serverDetail: TextView
    private lateinit var serverSwitch: Switch
    private lateinit var updateStatus: TextView
    private lateinit var updateAction: Button
    private lateinit var hotspotDetail: TextView

    private var ignoreSwitch = false
    private var holdSwitchUntil = 0L
    private var pendingStart: Boolean? = null
    private var downloadedApk: File? = null
    private var busy = false
    private var hotspotBusy = false
    private var destroyed = false

    private val statusPoll = Runnable { refreshServerStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        updates = AppUpdateClient(this)
        serverStatus = findViewById(R.id.server_status)
        serverDetail = findViewById(R.id.server_detail)
        serverSwitch = findViewById(R.id.server_switch)
        updateStatus = findViewById(R.id.update_status)
        updateAction = findViewById(R.id.update_action_button)
        findViewById<TextView>(R.id.version_label).text =
            getString(R.string.installed_version, BuildConfig.VERSION_NAME)

        val bootSwitch = findViewById<Switch>(R.id.boot_switch)
        bootSwitch.isChecked = BootSettings.isStartOnBoot(this)
        bootSwitch.setOnCheckedChangeListener { _, checked ->
            BootSettings.setStartOnBoot(this, checked)
        }
        val hotspotSwitch = findViewById<Switch>(R.id.hotspot_switch)
        hotspotSwitch.isChecked = BootSettings.isRestartHotspotOnBoot(this)
        hotspotSwitch.setOnCheckedChangeListener { _, checked ->
            BootSettings.setRestartHotspotOnBoot(this, checked)
        }
        hotspotDetail = findViewById(R.id.hotspot_detail)
        findViewById<Button>(R.id.restart_hotspot_button).setOnClickListener {
            restartHotspotNow()
        }
        serverSwitch.setOnCheckedChangeListener { _, checked ->
            if (ignoreSwitch) return@setOnCheckedChangeListener
            setServerEnabled(checked)
        }
        findViewById<Button>(R.id.check_update_button).setOnClickListener { checkForUpdate() }
        updateAction.setOnClickListener { onUpdateAction() }
    }

    override fun onResume() {
        super.onResume()
        refreshServerStatus()
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(statusPoll)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun onUi(block: () -> Unit) {
        if (destroyed) return
        runOnUiThread {
            if (!destroyed) block()
        }
    }

    private fun setServerEnabled(enabled: Boolean) {
        pendingStart = enabled
        holdSwitchUntil = SystemClock.elapsedRealtime() + HOLD_SWITCH_MS
        serverDetail.setText(if (enabled) R.string.server_starting else R.string.server_stopping)
        io.execute {
            val accepted = if (enabled) {
                HeadUnitServerStarter.start(applicationContext)
            } else {
                HeadUnitServerStarter.stop(applicationContext)
            }
            onUi {
                if (!accepted) {
                    pendingStart = null
                    holdSwitchUntil = 0L
                    serverDetail.setText(
                        if (enabled) R.string.server_start_failed else R.string.server_stop_failed,
                    )
                }
                scheduleStatusPolls()
            }
        }
    }

    private fun scheduleStatusPolls() {
        handler.removeCallbacks(statusPoll)
        handler.postDelayed(statusPoll, 700L)
        handler.postDelayed(statusPoll, HOLD_SWITCH_MS + 400L)
    }

    private fun refreshServerStatus() {
        io.execute {
            val listening = HeadUnitServerStatus.isListening()
            onUi { showServerStatus(listening) }
        }
    }

    private fun showServerStatus(listening: Boolean) {
        val holding = SystemClock.elapsedRealtime() < holdSwitchUntil
        val pending = pendingStart
        if (holding && pending != null && pending != listening) {
            return
        }
        pendingStart = null
        holdSwitchUntil = 0L
        ignoreSwitch = true
        serverSwitch.isChecked = listening
        ignoreSwitch = false
        serverStatus.setText(if (listening) R.string.server_running else R.string.server_stopped)
        if (pending != null && pending != listening) {
            serverDetail.setText(
                if (pending) R.string.server_still_stopped else R.string.server_still_running,
            )
        } else if (
            serverDetail.text == getString(R.string.server_starting) ||
            serverDetail.text == getString(R.string.server_stopping)
        ) {
            serverDetail.text = ""
        }
    }

    private fun restartHotspotNow() {
        if (hotspotBusy) return
        hotspotBusy = true
        hotspotDetail.setText(R.string.hotspot_restarting)
        io.execute {
            val result = HotspotRestarter.restart(applicationContext)
            onUi {
                hotspotBusy = false
                hotspotDetail.text = result.detail
            }
        }
    }

    private fun checkForUpdate() {
        if (busy) return
        busy = true
        downloadedApk = null
        updateAction.visibility = View.GONE
        updateStatus.setText(R.string.update_checking)
        io.execute {
            val result = try {
                Result.success(updates.checkForUpdate())
            } catch (error: Exception) {
                logUpdateFailure("Failed to check for update", error)
                Result.failure(error)
            }
            onUi {
                busy = false
                result.fold(
                    onSuccess = { check ->
                        when (check) {
                            UpdateCheck.UpToDate -> updateStatus.setText(R.string.update_up_to_date)
                            is UpdateCheck.Available -> {
                                updateStatus.text = getString(R.string.update_available, check.versionName)
                                updateAction.setText(R.string.download_update)
                                updateAction.tag = ACTION_DOWNLOAD
                                updateAction.visibility = View.VISIBLE
                            }
                        }
                    },
                    onFailure = {
                        updateStatus.setText(R.string.update_check_failed)
                    },
                )
            }
        }
    }

    private fun onUpdateAction() {
        if (busy) return
        when (updateAction.tag) {
            ACTION_DOWNLOAD -> downloadUpdate()
            ACTION_INSTALL -> downloadedApk?.let { launchApkInstall(it) }
        }
    }

    private fun downloadUpdate() {
        busy = true
        updateStatus.setText(R.string.update_downloading)
        io.execute {
            val result = try {
                Result.success(
                    updates.downloadApk { percent ->
                        onUi {
                            updateStatus.text = getString(R.string.update_downloading_percent, percent)
                        }
                    },
                )
            } catch (error: Exception) {
                logUpdateFailure("Failed to download APK", error)
                Result.failure(error)
            }
            onUi {
                busy = false
                result.fold(
                    onSuccess = { file ->
                        downloadedApk = file
                        updateStatus.setText(R.string.update_ready)
                        updateAction.setText(R.string.install_update)
                        updateAction.tag = ACTION_INSTALL
                        updateAction.visibility = View.VISIBLE
                    },
                    onFailure = {
                        updateStatus.setText(R.string.update_download_failed)
                        updateAction.visibility = View.GONE
                    },
                )
            }
        }
    }

    private fun launchApkInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                    },
                )
            } catch (_: ActivityNotFoundException) {
                updateStatus.setText(R.string.update_install_failed)
            }
            return
        }
        val uri = UpdateApkProvider.apkUri()
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri(apkFile.name, uri)
                },
            )
        } catch (_: ActivityNotFoundException) {
            updateStatus.setText(R.string.update_install_failed)
        }
    }

    companion object {
        private const val HOLD_SWITCH_MS = 2_000L
        private const val ACTION_DOWNLOAD = "download"
        private const val ACTION_INSTALL = "install"
    }
}
