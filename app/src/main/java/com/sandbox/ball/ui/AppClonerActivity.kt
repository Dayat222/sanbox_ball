package com.sandbox.ball.ui

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sandbox.ball.R
import com.sandbox.ball.core.container.AppContainer
import com.sandbox.ball.model.InstalledAppInfo
import com.sandbox.ball.ui.adapter.InstalledAppAdapter
import com.sandbox.ball.ui.view.StealthToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppClonerActivity : AppCompatActivity() {

    private lateinit var appContainer: AppContainer
    private lateinit var rvInstalledApps: RecyclerView
    private lateinit var adapter: InstalledAppAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var etSearch: EditText
    private lateinit var txtAppCount: TextView

    private var allApps: List<InstalledAppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_cloner)

        appContainer = AppContainer.getInstance(this)

        rvInstalledApps = findViewById(R.id.rv_installed_apps)
        progressBar = findViewById(R.id.progress_cloner)
        etSearch = findViewById(R.id.et_search_apps)
        txtAppCount = findViewById(R.id.txt_installed_count)

        setupRecyclerView()
        setupSearch()
        loadInstalledApps()
    }

    private fun setupRecyclerView() {
        adapter = InstalledAppAdapter(
            apps = emptyList(),
            onCloneClick = { app ->
                cloneSelectedApp(app)
            }
        )
        rvInstalledApps.layoutManager = LinearLayoutManager(this)
        rvInstalledApps.adapter = adapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    @SuppressLint("SetTextI18n")
    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        txtAppCount.text = "${filtered.size} Aplikasi Ditemukan"
        adapter.updateData(filtered)
    }

    private fun loadInstalledApps() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val clonedPackages = appContainer.getClonedApps().map { it.packageName }.toSet()

            val list = mutableListOf<InstalledAppInfo>()
            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                // Don't list sandbox itself
                if (pkg.packageName == packageName) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = pm.getApplicationLabel(appInfo).toString()
                val versionName = pkg.versionName ?: "1.0"
                val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pkg.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkg.versionCode.toLong()
                }

                list.add(
                    InstalledAppInfo(
                        packageName = pkg.packageName,
                        appName = appName,
                        versionName = versionName,
                        versionCode = versionCode,
                        sourceDir = appInfo.sourceDir,
                        isSystemApp = isSystem,
                        isAlreadyCloned = clonedPackages.contains(pkg.packageName)
                    )
                )
            }

            // Sort: non-system apps first, then alphabetically
            list.sortWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))

            withContext(Dispatchers.Main) {
                allApps = list
                progressBar.visibility = View.GONE
                filterApps(etSearch.text.toString())
            }
        }
    }

    private fun cloneSelectedApp(app: InstalledAppInfo) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cloned = appContainer.cloneApp(app.packageName)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    // PRD UI requirement: bottom toast notification pop-up without blocking dialog
                    StealthToast.show(
                        this@AppClonerActivity,
                        "Aplikasi ${cloned.appName} berhasil dikloning ke sandbox!"
                    )
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    StealthToast.show(
                        this@AppClonerActivity,
                        "Gagal mengkloning: ${e.message}"
                    )
                }
            }
        }
    }
}
