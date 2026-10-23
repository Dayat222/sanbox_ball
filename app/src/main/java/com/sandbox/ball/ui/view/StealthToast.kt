package com.sandbox.ball.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.sandbox.ball.R

object StealthToast {
    /**
     * PRD UI Requirement:
     * Menggunakan toast notification pop-up di bagian bawah layar saat profil
     * identitas berhasil direset atau aplikasi selesai dikloning, sehingga tidak ada
     * dialog box yang menghalangi layar.
     */
    @SuppressLint("InflateParams")
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast(context)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.custom_stealth_toast, null)
        val text = view.findViewById<TextView>(R.id.txt_toast_message)
        text.text = message

        toast.view = view
        toast.duration = duration
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 140)
        toast.show()
    }
}
