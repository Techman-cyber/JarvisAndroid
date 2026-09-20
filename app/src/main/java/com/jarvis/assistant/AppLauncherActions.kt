package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object AppLauncherActions {

    /** Returns the display label of the app it launched, or null if none matched. */
    fun launch(ctx: Context, spokenName: String): String? {
        return try {
            val pm = ctx.packageManager
            val apps: List<ApplicationInfo> = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val target = normalize(spokenName)
            if (target.isBlank()) return null

            // Only consider apps that can actually be launched — many entries
            // returned here are background components or services with no
            // launcher activity at all. Matching against those first and then
            // discovering they can't be opened was the bug: it gave up
            // entirely instead of trying the next candidate.
            data class Candidate(val info: ApplicationInfo, val label: String, val normalized: String, val intent: Intent)
            val launchable = apps.mapNotNull { app ->
                if (app.packageName == ctx.packageName) return@mapNotNull null
                val intent = pm.getLaunchIntentForPackage(app.packageName) ?: return@mapNotNull null
                val label = pm.getApplicationLabel(app).toString()
                Candidate(app, label, normalize(label), intent)
            }

            val exact = launchable.firstOrNull { it.normalized == target }
            val startsWith = launchable.firstOrNull { it.normalized.startsWith(target) || target.startsWith(it.normalized) }
            val contains = launchable.firstOrNull { it.normalized.contains(target) || target.contains(it.normalized) }
            val chosen = exact ?: startsWith ?: contains ?: return null

            SystemLauncher.launch(ctx, chosen.intent, chosen.label)
            chosen.label
        } catch (e: Exception) {
            // Some OEM builds restrict getInstalledApplications even with the
            // QUERY_ALL_PACKAGES permission declared; fail quietly rather than
            // crashing the whole background service.
            null
        }
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
}
