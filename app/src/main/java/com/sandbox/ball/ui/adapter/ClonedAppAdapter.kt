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
import com.sandbox.ball.model.ClonedAppInfo

class ClonedAppAdapter(
    private var apps: List<ClonedAppInfo>,
    private val onLaunchClick: (ClonedAppInfo) -> Unit,
    private val onResetClick: (ClonedAppInfo) -> Unit,
    private val onRemoveClick: (ClonedAppInfo) -> Unit
) : RecyclerView.Adapter<ClonedAppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcon: ImageView = view.findViewById(R.id.img_app_icon)
        val txtName: TextView = view.findViewById(R.id.txt_app_name)
        val txtPackage: TextView = view.findViewById(R.id.txt_app_package)
        val txtProfile: TextView = view.findViewById(R.id.txt_app_profile)
        val btnLaunch: Button = view.findViewById(R.id.btn_launch_app)
        val btnReset: Button = view.findViewById(R.id.btn_reset_app)
        val btnRemove: Button = view.findViewById(R.id.btn_remove_app)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cloned_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.txtName.text = app.appName
        holder.txtPackage.text = app.packageName
        holder.txtProfile.text = "Profile: ${app.profileName}"

        // Load icon from package manager if available
        try {
            val pm = holder.itemView.context.packageManager
            val icon: Drawable = pm.getApplicationIcon(app.packageName)
            holder.imgIcon.setImageDrawable(icon)
        } catch (_: PackageManager.NameNotFoundException) {
            holder.imgIcon.setImageResource(R.drawable.ic_stealth_shield)
        }

        holder.btnLaunch.setOnClickListener { onLaunchClick(app) }
        holder.btnReset.setOnClickListener { onResetClick(app) }
        holder.btnRemove.setOnClickListener { onRemoveClick(app) }
    }

    override fun getItemCount(): Int = apps.size

    fun updateData(newApps: List<ClonedAppInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }
}
