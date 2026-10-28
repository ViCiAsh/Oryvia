package com.damweeb.oryvia

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = runBlocking { SettingsRepository(context).flow.first() }
        if (settings.enabled && Perms.overlay(context)) {
            IslandService.start(context)
        }
    }
}
