package com.damweeb.oryvia

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.islandDataStore by preferencesDataStore(name = "island_settings")

data class IslandSettings(
    val enabled: Boolean = true,
    val mode: IslandMode = IslandMode.PILL,

    // Expanded shape
    val expandedWidthDp: Int = 300,
    val expandedHeightDp: Int = 76,
    val cornerRadiusDp: Int = 30,
    val expandedOffsetXDp: Int = 0,
    val expandedOffsetYDp: Int = 0,

    // Idle shape (what stays on screen after the island hides)
    val idleVisible: Boolean = true,
    val idleWidthDp: Int = 96,
    val idleHeightDp: Int = 26,
    val idleOpacity: Float = 1f,
    val idleColor: Int = 0xFF000000.toInt(),

    // Notch / pill position
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 8,

    // Punch hole calibration (defaults are estimates for the realme 7 Pro, adjust in settings)
    val punchXDp: Int = 12,
    val punchYDp: Int = 0,
    val punchSizeDp: Int = 28,

    // Behaviour
    val hideDelaySec: Float = 4f,
    val animationSpeed: Float = 1f,
    val showNotifications: Boolean = true,
    val showMedia: Boolean = true,
    val showCharging: Boolean = true,
    val hideInLandscape: Boolean = true,

    // Colors (ARGB)
    val backgroundColor: Int = 0xFF000000.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accentColor: Int = 0xFF4ADE80.toInt(),
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.islandDataStore

    val flow: Flow<IslandSettings> = store.data.map { read(it) }

    suspend fun update(transform: (IslandSettings) -> IslandSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private object K {
        val enabled = booleanPreferencesKey("enabled")
        val mode = stringPreferencesKey("mode")
        val expandedWidth = intPreferencesKey("expanded_width")
        val expandedHeight = intPreferencesKey("expanded_height")
        val cornerRadius = intPreferencesKey("corner_radius")
        val idleVisible = booleanPreferencesKey("idle_visible")
        val idleWidth = intPreferencesKey("idle_width")
        val idleHeight = intPreferencesKey("idle_height")
        val idleOpacity = floatPreferencesKey("idle_opacity")
        val idleColor = intPreferencesKey("idle_color")
        val offsetX = intPreferencesKey("offset_x")
        val offsetY = intPreferencesKey("offset_y")
        val expandedOffsetX = intPreferencesKey("expanded_offset_x")
        val expandedOffsetY = intPreferencesKey("expanded_offset_y")
        val punchX = intPreferencesKey("punch_x")
        val punchY = intPreferencesKey("punch_y")
        val punchSize = intPreferencesKey("punch_size")
        val hideDelay = floatPreferencesKey("hide_delay")
        val animationSpeed = floatPreferencesKey("animation_speed")
        val showNotifications = booleanPreferencesKey("show_notifications")
        val showMedia = booleanPreferencesKey("show_media")
        val showCharging = booleanPreferencesKey("show_charging")
        val hideInLandscape = booleanPreferencesKey("hide_in_landscape")
        val backgroundColor = intPreferencesKey("background_color")
        val textColor = intPreferencesKey("text_color")
        val accentColor = intPreferencesKey("accent_color")
    }

    private fun read(p: Preferences): IslandSettings {
        val d = IslandSettings()
        return IslandSettings(
            enabled = p[K.enabled] ?: d.enabled,
            mode = IslandMode.entries.firstOrNull { it.name == p[K.mode] } ?: d.mode,
            expandedWidthDp = p[K.expandedWidth] ?: d.expandedWidthDp,
            expandedHeightDp = p[K.expandedHeight] ?: d.expandedHeightDp,
            cornerRadiusDp = p[K.cornerRadius] ?: d.cornerRadiusDp,
            idleVisible = p[K.idleVisible] ?: d.idleVisible,
            idleWidthDp = p[K.idleWidth] ?: d.idleWidthDp,
            idleHeightDp = p[K.idleHeight] ?: d.idleHeightDp,
            idleOpacity = p[K.idleOpacity] ?: d.idleOpacity,
            idleColor = p[K.idleColor] ?: d.idleColor,
            offsetXDp = p[K.offsetX] ?: d.offsetXDp,
            offsetYDp = p[K.offsetY] ?: d.offsetYDp,
            expandedOffsetXDp = p[K.expandedOffsetX] ?: d.expandedOffsetXDp,
            expandedOffsetYDp = p[K.expandedOffsetY] ?: d.expandedOffsetYDp,
            punchXDp = p[K.punchX] ?: d.punchXDp,
            punchYDp = p[K.punchY] ?: d.punchYDp,
            punchSizeDp = p[K.punchSize] ?: d.punchSizeDp,
            hideDelaySec = p[K.hideDelay] ?: d.hideDelaySec,
            animationSpeed = p[K.animationSpeed] ?: d.animationSpeed,
            showNotifications = p[K.showNotifications] ?: d.showNotifications,
            showMedia = p[K.showMedia] ?: d.showMedia,
            showCharging = p[K.showCharging] ?: d.showCharging,
            hideInLandscape = p[K.hideInLandscape] ?: d.hideInLandscape,
            backgroundColor = p[K.backgroundColor] ?: d.backgroundColor,
            textColor = p[K.textColor] ?: d.textColor,
            accentColor = p[K.accentColor] ?: d.accentColor,
        )
    }

    private fun write(p: MutablePreferences, s: IslandSettings) {
        p[K.enabled] = s.enabled
        p[K.mode] = s.mode.name
        p[K.expandedWidth] = s.expandedWidthDp
        p[K.expandedHeight] = s.expandedHeightDp
        p[K.cornerRadius] = s.cornerRadiusDp
        p[K.idleVisible] = s.idleVisible
        p[K.idleWidth] = s.idleWidthDp
        p[K.idleHeight] = s.idleHeightDp
        p[K.idleOpacity] = s.idleOpacity
        p[K.idleColor] = s.idleColor
        p[K.offsetX] = s.offsetXDp
        p[K.offsetY] = s.offsetYDp
        p[K.expandedOffsetX] = s.expandedOffsetXDp
        p[K.expandedOffsetY] = s.expandedOffsetYDp
        p[K.punchX] = s.punchXDp
        p[K.punchY] = s.punchYDp
        p[K.punchSize] = s.punchSizeDp
        p[K.hideDelay] = s.hideDelaySec
        p[K.animationSpeed] = s.animationSpeed
        p[K.showNotifications] = s.showNotifications
        p[K.showMedia] = s.showMedia
        p[K.showCharging] = s.showCharging
        p[K.hideInLandscape] = s.hideInLandscape
        p[K.backgroundColor] = s.backgroundColor
        p[K.textColor] = s.textColor
        p[K.accentColor] = s.accentColor
    }
}
