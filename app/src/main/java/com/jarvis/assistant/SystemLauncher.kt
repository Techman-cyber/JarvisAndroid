package com.jarvis.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Why this exists: Android 10+ silently blocks an app from starting an
 * Activity when it has no visible UI — no exception, no crash, the intent
 * just does nothing. A foreground service is *usually* exempt, but not
 * guaranteed to be on every Android version/OEM skin, which is exactly why
 * "open YouTube" can work while Jarvis's own screen is open and do nothing
 * at all when it's only running quietly in the background.
 *
 * The fix Android itself expects apps to use: a tappable notification.
 * Tapping a notification always counts as user interaction, so its
 * PendingIntent is never subject to this restriction. This tries the direct,
 * instant launch first (works whenever it's allowed to), and always also
 * leaves a real, guaranteed-to-work fallback behind regardless.
 */
object SystemLauncher {
    private const val CHANNEL_ID = "jarvis_launch_channel"
    private var nextNotifId = 5000

    fun launch(ctx: Context, intent: Intent, label: String) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // fall through — the notification below still gives the user a
            // guaranteed way to open it.
        }
        postFallbackNotification(ctx, intent, label)
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Jarvis actions", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }

    private fun postFallbackNotification(ctx: Context, intent: Intent, label: String) {
        ensureChannel(ctx)
        val id = nextNotifId++
        val pending = PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("Tap to open $label")
            .setContentText("Jarvis tried to open this in the background — tap here if it didn't appear")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(id, notification)
    }
}
