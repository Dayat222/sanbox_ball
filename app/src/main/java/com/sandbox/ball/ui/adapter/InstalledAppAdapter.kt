package com.sandbox.ball.ui.adapter

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sandbox.ball.R
import com.sandbox.ball.model.InstalledAppInfo

class InstalledAppAdapter(
    private var apps: List<InstalledAppInfo>,
    private val onCloneClick: (InstalledAppInfo) -> Unit
) : RecyclerView.Adapter<InstalledAppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcon: ImageView = view.findViewById(R.id.img_inst_app_icon)
        val txtName: TextView = view.findViewById(R.id.txt_inst_app_name)
        val txtPackage: TextView = view.findViewById(R.id.txt_inst_app_package)
        val txtVersion: TextView = view.findViewById(R.id.txt_inst_app_version)
        val btnClone: Button = view.findViewById(R.id.btn_inst_clone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installed_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.txtName.text = app.appName
        holder.txtPackage.text = app.packageName
        holder.txtVersion.text = "v${app.versionName}"

        try {
            val pm = holder.itemView.context.packageManager
            val icon: Drawable = pm.getApplicationIcon(app.packageName)
            holder.imgIcon.setImageDrawable(icon)
        } catch (_: PackageManager.NameNotFoundException) {
            holder.imgIcon.setImageResource(R.drawable.ic_stealth_shield)
        }

        if (app.isAlreadyCloned) {
            holder.btnClone.text = "CLONED"
            holder.btnClone.isEnabled = false
            holder.btnClone.alpha = 0.5f
        } else {
            holder.btnClone.text = "CLONE"
            holder.btnClone.isEnabled = true
            holder.btnClone.alpha = 1.0f
            holder.btnClone.setOnClickListener { onCloneClick(app) }
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateData(newApps: List<InstalledAppInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }
}
