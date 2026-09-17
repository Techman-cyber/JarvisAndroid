package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object AppLauncherActions {

    /** Returns the display label of the app it launched, or null if none matched. */
    fun launch(ctx: Context, spokenName: String): String? {
        val pm = ctx.packageManager
        val apps: List<ApplicationInfo> = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val target = spokenName.lowercase().replace(" ", "")

        var exact: ApplicationInfo? = null
        var exactLabel = ""
        var partial: ApplicationInfo? = null
        var partialLabel = ""

        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString()
            val normalized = label.lowercase().replace(" ", "")
            if (normalized == target) {
                exact = app; exactLabel = label; break
            }
            if (partial == null && (normalized.contains(target) || target.contains(normalized))) {
                partial = app; partialLabel = label
            }
        }

        val chosen = exact ?: partial ?: return null
        val chosenLabel = if (exact != null) exactLabel else partialLabel
        val launchIntent = pm.getLaunchIntentForPackage(chosen.packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launchIntent)
        return chosenLabel
    }
}
