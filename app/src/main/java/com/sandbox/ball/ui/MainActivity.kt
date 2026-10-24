package com.sandbox.ball.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sandbox.ball.BuildConfig
import com.sandbox.ball.R
import com.sandbox.ball.core.container.AppContainer
import com.sandbox.ball.core.hooks.HookManager
import com.sandbox.ball.core.spoofer.HardwareProfile
import com.sandbox.ball.core.spoofer.ProfileManager
import com.sandbox.ball.core.updater.AppUpdateInfo
import com.sandbox.ball.core.updater.UpdateManager
import com.sandbox.ball.model.ClonedAppInfo
import com.sandbox.ball.ui.adapter.ClonedAppAdapter
import com.sandbox.ball.ui.view.StealthToast
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var profileManager: ProfileManager
    private lateinit var appContainer: AppContainer
    private lateinit var hookManager: HookManager

    private lateinit var txtShieldCount: TextView
    private lateinit var txtActiveModel: TextView
    private lateinit var txtActiveImei: TextView
    private lateinit var txtActiveAndroidId: TextView
    private lateinit var txtActiveMac: TextView
    private lateinit var txtEmptyState: TextView
    private lateinit var rvClonedApps: RecyclerView
    private lateinit var adapter: ClonedAppAdapter

    private lateinit var layoutUpdateBanner: LinearLayout
    private lateinit var txtUpdateBannerTitle: TextView
    private lateinit var btnBannerUpdate: Button

    private var availableUpdate: AppUpdateInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profileManager = ProfileManager.getInstance(this)
        appContainer = AppContainer.getInstance(this)
        hookManager = HookManager.getInstance(this)

        initViews()
        setupRecyclerView()
        refreshDashboard()

        // Check for in-app updates in background on launch
        checkForAppUpdate(silent = true)
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun initViews() {
        txtShieldCount = findViewById(R.id.txt_shield_count)
        txtActiveModel = findViewById(R.id.txt_active_model)
        txtActiveImei = findViewById(R.id.txt_active_imei)
        txtActiveAndroidId = findViewById(R.id.txt_active_android_id)
        txtActiveMac = findViewById(R.id.txt_active_mac)
        txtEmptyState = findViewById(R.id.txt_empty_state)
        rvClonedApps = findViewById(R.id.rv_cloned_apps)

        layoutUpdateBanner = findViewById(R.id.layout_update_banner)
        txtUpdateBannerTitle = findViewById(R.id.txt_update_banner_title)
        btnBannerUpdate = findViewById(R.id.btn_banner_update)

        val btnOneClickReset = findViewById<Button>(R.id.btn_one_click_reset)
        val btnCloneApp = findViewById<Button>(R.id.btn_open_cloner)
        val btnProfileManager = findViewById<Button>(R.id.btn_open_profile)
        val txtVersionHeader = findViewById<TextView>(R.id.txt_app_version_header)
        txtVersionHeader.text = "v${BuildConfig.VERSION_NAME} • Periksa Pembaruan"

        // Manual check for update when clicking version in header
        txtVersionHeader.setOnClickListener {
            checkForAppUpdate(silent = false)
        }

        btnBannerUpdate.setOnClickListener {
            availableUpdate?.let { showUpdateDialog(it) }
        }

        // PRD Core Feature: 1-Click Reset
        btnOneClickReset.setOnClickListener {
            val newProfile = profileManager.rotateIdentity(clearSandboxCache = true)
            refreshDashboard()
            StealthToast.show(
                this,
                "Identitas Berhasil Direset ke ${newProfile.model} (IMEI: ${newProfile.imei.take(8)}...)"
            )
        }

        btnCloneApp.setOnClickListener {
            startActivity(Intent(this, AppClonerActivity::class.java))
        }

        btnProfileManager.setOnClickListener {
            startActivity(Intent(this, ProfileDetailActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = ClonedAppAdapter(
            apps = emptyList(),
            onLaunchClick = { app ->
                appContainer.launchApp(app)
            },
            onResetClick = { app ->
                val newProfile = appContainer.resetApp(app.packageName)
                refreshDashboard()
                StealthToast.show(
                    this,
                    "Profil ${app.appName} direset ke ${newProfile.model}!"
                )
            },
            onRemoveClick = { app ->
                appContainer.removeClonedApp(app.packageName)
                refreshDashboard()
                StealthToast.show(this, "Aplikasi ${app.appName} dihapus dari sandbox.")
            }
        )
        rvClonedApps.layoutManager = LinearLayoutManager(this)
        rvClonedApps.adapter = adapter
    }

    @SuppressLint("SetTextI18n")
    private fun refreshDashboard() {
        val activeProfile = profileManager.getActiveProfile() ?: HardwareProfile()
        val evasionStatus = hookManager.getEvasionStatus()

        txtShieldCount.text = "${evasionStatus.getActiveShieldCount()}/${evasionStatus.getTotalShieldCount()} SHIELDS ACTIVE"
        txtActiveModel.text = "• Model: ${activeProfile.brand.uppercase()} ${activeProfile.model}"
        txtActiveImei.text = "• IMEI: ${activeProfile.imei}"
        txtActiveAndroidId.text = "• Android ID: ${activeProfile.androidId}"
        txtActiveMac.text = "• MAC: ${activeProfile.macAddress} (${activeProfile.wifiSsid})"

        val apps = appContainer.getClonedApps()
        if (apps.isEmpty()) {
            txtEmptyState.visibility = View.VISIBLE
            rvClonedApps.visibility = View.GONE
        } else {
            txtEmptyState.visibility = View.GONE
            rvClonedApps.visibility = View.VISIBLE
            adapter.updateData(apps)
        }
    }

    /**
     * In-App Update Engine: Checks GitHub Releases API for new version
     */
    private fun checkForAppUpdate(silent: Boolean = false) {
        if (!silent) {
            StealthToast.show(this, "Memeriksa pembaruan aplikasi...")
        }

        lifecycleScope.launch {
            val updateInfo = UpdateManager.checkForUpdate()
            if (updateInfo != null && updateInfo.hasUpdate) {
                availableUpdate = updateInfo
                layoutUpdateBanner.visibility = View.VISIBLE
                txtUpdateBannerTitle.text = "Pembaruan ${updateInfo.latestVersion} Tersedia!"
                showUpdateDialog(updateInfo)
            } else {
                layoutUpdateBanner.visibility = View.GONE
                if (!silent) {
                    StealthToast.show(
                        this@MainActivity,
                        "Aplikasi sudah versi terbaru (v${BuildConfig.VERSION_NAME})"
                    )
                }
            }
        }
    }

    /**
     * Shows In-App Update Modal with download progress and 1-click seamless installer
     */
    @SuppressLint("SetTextI18n")
    private fun showUpdateDialog(updateInfo: AppUpdateInfo) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_update, null)
        val txtVersion = dialogView.findViewById<TextView>(R.id.txt_update_version)
        val txtNotes = dialogView.findViewById<TextView>(R.id.txt_update_notes)
        val layoutProgress = dialogView.findViewById<LinearLayout>(R.id.layout_download_progress)
        val txtProgress = dialogView.findViewById<TextView>(R.id.txt_download_status)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_update_download)
        val layoutButtons = dialogView.findViewById<LinearLayout>(R.id.layout_update_buttons)
        val btnUpdateNow = dialogView.findViewById<Button>(R.id.btn_update_now)
        val btnUpdateLater = dialogView.findViewById<Button>(R.id.btn_update_later)

        txtVersion.text = "Versi Baru: ${updateInfo.latestVersion} • Versi Anda: v${BuildConfig.VERSION_NAME}"
        txtNotes.text = updateInfo.releaseNotes.ifEmpty { "Pembaruan kode dan peningkatan stealth engine." }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnUpdateLater.setOnClickListener {
            dialog.dismiss()
        }

        btnUpdateNow.setOnClickListener {
            btnUpdateNow.isEnabled = false
            btnUpdateLater.visibility = View.GONE
            layoutProgress.visibility = View.VISIBLE
            dialog.setCancelable(false)

            lifecycleScope.launch {
                val apkFile = UpdateManager.downloadApk(
                    context = this@MainActivity,
                    downloadUrl = updateInfo.downloadUrl,
                    onProgress = { percent ->
                        progressBar.progress = percent
                        txtProgress.text = "Mengunduh pembaruan: $percent%"
                    }
                )

                if (apkFile != null && apkFile.exists()) {
                    txtProgress.text = "Menginstal pembaruan..."
                    dialog.dismiss()
                    // Installs APK directly via FileProvider after integrity check (SEC-04)
                    UpdateManager.installApk(this@MainActivity, apkFile, updateInfo.sha256Checksum)
                } else {
                    txtProgress.text = "Gagal mengunduh pembaruan"
                    btnUpdateNow.isEnabled = true
                    btnUpdateLater.visibility = View.VISIBLE
                    dialog.setCancelable(true)
                    StealthToast.show(this@MainActivity, "Gagal mengunduh APK pembaruan")
                }
            }
        }

        dialog.show()
    }
}
