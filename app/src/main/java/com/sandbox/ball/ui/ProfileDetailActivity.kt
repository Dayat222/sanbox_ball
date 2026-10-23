package com.sandbox.ball.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.sandbox.ball.R
import com.sandbox.ball.core.spoofer.HardwareProfile
import com.sandbox.ball.core.spoofer.ProfileGenerator
import com.sandbox.ball.core.spoofer.ProfileManager
import com.sandbox.ball.ui.view.StealthToast

class ProfileDetailActivity : AppCompatActivity() {

    private lateinit var profileManager: ProfileManager
    private var currentProfile: HardwareProfile = HardwareProfile()

    private lateinit var etModel: EditText
    private lateinit var etBrand: EditText
    private lateinit var etManufacturer: EditText
    private lateinit var etImei: EditText
    private lateinit var etAndroidId: EditText
    private lateinit var etMac: EditText
    private lateinit var etSerial: EditText
    private lateinit var etWifiSsid: EditText
    private lateinit var etCarrier: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_detail)

        profileManager = ProfileManager.getInstance(this)
        currentProfile = profileManager.getActiveProfile() ?: HardwareProfile()

        initViews()
        populateFields(currentProfile)
    }

    private fun initViews() {
        etModel = findViewById(R.id.et_profile_model)
        etBrand = findViewById(R.id.et_profile_brand)
        etManufacturer = findViewById(R.id.et_profile_manufacturer)
        etImei = findViewById(R.id.et_profile_imei)
        etAndroidId = findViewById(R.id.et_profile_android_id)
        etMac = findViewById(R.id.et_profile_mac)
        etSerial = findViewById(R.id.et_profile_serial)
        etWifiSsid = findViewById(R.id.et_profile_wifi_ssid)
        etCarrier = findViewById(R.id.et_profile_carrier)

        val btnPresetSamsung = findViewById<Button>(R.id.btn_preset_samsung)
        val btnPresetPixel = findViewById<Button>(R.id.btn_preset_pixel)
        val btnPresetXiaomi = findViewById<Button>(R.id.btn_preset_xiaomi)
        val btnPresetRog = findViewById<Button>(R.id.btn_preset_rog)
        val btnRandomize = findViewById<Button>(R.id.btn_randomize_profile)
        val btnSave = findViewById<Button>(R.id.btn_save_profile)

        btnPresetSamsung.setOnClickListener {
            currentProfile = ProfileGenerator.generatePreset(ProfileGenerator.DevicePreset.SAMSUNG_S24_ULTRA)
            populateFields(currentProfile)
        }

        btnPresetPixel.setOnClickListener {
            currentProfile = ProfileGenerator.generatePreset(ProfileGenerator.DevicePreset.GOOGLE_PIXEL_8_PRO)
            populateFields(currentProfile)
        }

        btnPresetXiaomi.setOnClickListener {
            currentProfile = ProfileGenerator.generatePreset(ProfileGenerator.DevicePreset.XIAOMI_14)
            populateFields(currentProfile)
        }

        btnPresetRog.setOnClickListener {
            currentProfile = ProfileGenerator.generatePreset(ProfileGenerator.DevicePreset.ASUS_ROG_8)
            populateFields(currentProfile)
        }

        btnRandomize.setOnClickListener {
            currentProfile = ProfileGenerator.generateRandomProfile("Custom Stealth Identity")
            populateFields(currentProfile)
            StealthToast.show(this, "Nilai hardware diacak secara otomatis")
        }

        btnSave.setOnClickListener {
            val updated = currentProfile.copy(
                model = etModel.text.toString().trim(),
                brand = etBrand.text.toString().trim(),
                manufacturer = etManufacturer.text.toString().trim(),
                imei = etImei.text.toString().trim(),
                androidId = etAndroidId.text.toString().trim(),
                macAddress = etMac.text.toString().trim(),
                serialNumber = etSerial.text.toString().trim(),
                wifiSsid = etWifiSsid.text.toString().trim(),
                networkOperatorName = etCarrier.text.toString().trim()
            )
            profileManager.setActiveProfile(updated)
            profileManager.saveProfile(updated)

            StealthToast.show(
                this,
                "Profil tersimpan: ${updated.model} (IMEI: ${updated.imei.take(8)}...)"
            )
            finish()
        }
    }

    private fun populateFields(p: HardwareProfile) {
        etModel.setText(p.model)
        etBrand.setText(p.brand)
        etManufacturer.setText(p.manufacturer)
        etImei.setText(p.imei)
        etAndroidId.setText(p.androidId)
        etMac.setText(p.macAddress)
        etSerial.setText(p.serialNumber)
        etWifiSsid.setText(p.wifiSsid)
        etCarrier.setText(p.networkOperatorName)
    }
}
