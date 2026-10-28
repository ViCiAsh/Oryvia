package com.damweeb.oryvia

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Perms {
    fun overlay(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun notificationAccess(c: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun batteryExempt(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    /** Only needed on Android 13+ so the "island is running" notification can be shown. */
    fun postNotifications(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** The two permissions the island cannot work without. */
    fun requiredGranted(c: Context): Boolean = overlay(c) && notificationAccess(c)

    fun isColorOsFamily(): Boolean =
        Build.MANUFACTURER.lowercase() in setOf("realme", "oppo", "oneplus")
}

fun Context.openOverlaySettings() {
    startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
    )
}

fun Context.openNotificationAccessSettings() {
    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

@SuppressLint("BatteryLife")
fun Context.requestBatteryExemption() {
    startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
    )
}

/**
 * realme / ColorOS keeps a separate "auto-start" list with no public API.
 * We try the known settings screens one by one and fall back to the app info page.
 */
fun Context.openAutoStartSettings() {
    val candidates = listOf(
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.battery" to "com.oplus.startupapp.view.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
    )
    for ((pkg, cls) in candidates) {
        try {
            startActivity(Intent().setClassName(pkg, cls))
            return
        } catch (_: Exception) {
            // try the next one
        }
    }
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    )
}
