package com.sandbox.ball.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sandbox.ball.R
import com.sandbox.ball.core.container.AppContainer
import com.sandbox.ball.core.hooks.HookManager
import com.sandbox.ball.core.spoofer.HardwareProfile
import com.sandbox.ball.core.spoofer.ProfileManager
import com.sandbox.ball.model.ClonedAppInfo
import com.sandbox.ball.ui.adapter.ClonedAppAdapter
import com.sandbox.ball.ui.view.StealthToast

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profileManager = ProfileManager.getInstance(this)
        appContainer = AppContainer.getInstance(this)
        hookManager = HookManager.getInstance(this)

        initViews()
        setupRecyclerView()
        refreshDashboard()
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

        val btnOneClickReset = findViewById<Button>(R.id.btn_one_click_reset)
        val btnCloneApp = findViewById<Button>(R.id.btn_open_cloner)
        val btnProfileManager = findViewById<Button>(R.id.btn_open_profile)

        // PRD Core Feature: 1-Click Reset
        btnOneClickReset.setOnClickListener {
            val newProfile = profileManager.rotateIdentity(clearSandboxCache = true)
            refreshDashboard()
            // PRD UI requirement: bottom toast notification pop-up without blocking dialog
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
}
