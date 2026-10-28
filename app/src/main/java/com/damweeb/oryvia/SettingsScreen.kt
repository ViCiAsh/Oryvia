package com.damweeb.oryvia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext

/**
 * Every control writes straight to DataStore, and the running service observes the same
 * DataStore, so the island on your screen updates live while you drag a slider.
 * The bar pinned at the bottom re-fires a test event so you can see the expanded island again.
 */
@Composable
fun SettingsScreen(
    s: IslandSettings,
    update: (IslandSettings.() -> IslandSettings) -> Unit,
    onOpenSetup: () -> Unit,
) {
    val punch = s.mode == IslandMode.PUNCH_HOLE

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IslandHeader(enabled = s.enabled) { v -> update { copy(enabled = v) } }

            // ------------------------------------------------------------ mode
            SettingsCard(
                title = "Mode",
                subtitle = s.mode.label,
                defaultOpen = true,
            ) {
                ModeSelector(selected = s.mode) { m -> update { copy(mode = m) } }
                Text(
                    text = when (s.mode) {
                        IslandMode.NOTCH -> "Hangs from the top edge, centered. Expands to both sides."
                        IslandMode.PILL -> "Floats below the top edge, centered. Expands to both sides."
                        IslandMode.PUNCH_HOLE -> "Sits around the camera hole and expands from left to right."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ------------------------------------------------------------ size
            SettingsCard(title = "Size & shape", subtitle = "Expanded width, height, corners") {
                IntSlider("Width", s.expandedWidthDp, 160..380) { v ->
                    update { copy(expandedWidthDp = v) }
                }
                IntSlider("Height", s.expandedHeightDp, 56..140) { v ->
                    update { copy(expandedHeightDp = v) }
                }
                IntSlider("Corner radius", s.cornerRadiusDp, 0..60) { v ->
                    update { copy(cornerRadiusDp = v) }
                }
            }

            // ------------------------------------------------------------ idle
            SettingsCard(title = "Idle shape", subtitle = "What stays on screen after it hides") {
                SwitchRow("Show idle shape", s.idleVisible) { v ->
                    update { copy(idleVisible = v) }
                }
                if (punch) {
                    Text(
                        "In punch hole mode the idle shape is a circle with the camera diameter " +
                                "set in the Position card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    IntSlider("Idle width", s.idleWidthDp, 24..240) { v ->
                        update { copy(idleWidthDp = v) }
                    }
                    IntSlider("Idle height", s.idleHeightDp, 6..60) { v ->
                        update { copy(idleHeightDp = v) }
                    }
                }
                FloatSlider(
                    label = "Opacity",
                    value = s.idleOpacity,
                    range = 0f..1f,
                    format = { "${(it * 100).roundToInt()}%" },
                ) { v -> update { copy(idleOpacity = (v * 100).roundToInt() / 100f) } }
                ColorRow("Color", s.idleColor) { c -> update { copy(idleColor = c) } }
            }

            // ------------------------------------------------------------ position
            SettingsCard(
                title = "Position",
                subtitle = if (punch) "Camera calibration and expanded position" else "Idle and expanded position",
            ) {
                if (punch) {
                    Text(
                        "Drag until the idle circle sits exactly over your camera hole.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IntSlider("Left edge (X)", s.punchXDp, -20..160) { v ->
                        update { copy(punchXDp = v) }
                    }
                    IntSlider("Top edge (Y)", s.punchYDp, -20..80) { v ->
                        update { copy(punchYDp = v) }
                    }
                    IntSlider("Camera diameter", s.punchSizeDp, 12..64) { v ->
                        update { copy(punchSizeDp = v) }
                    }
                } else {
                    IntSlider("Horizontal offset", s.offsetXDp, -160..160) { v ->
                        update { copy(offsetXDp = v) }
                    }
                    if (s.mode == IslandMode.PILL) {
                        IntSlider("Distance from top", s.offsetYDp, 0..80) { v ->
                            update { copy(offsetYDp = v) }
                        }
                    } else {
                        Text(
                            "The notch always sits flush with the top edge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()
                SubLabel("When expanded")
                IntSlider(
                    label = "Horizontal shift",
                    value = if (punch) s.expandedOffsetXDp.coerceAtLeast(0) else s.expandedOffsetXDp,
                    range = if (punch) 0..120 else -120..120,
                ) { v -> update { copy(expandedOffsetXDp = v) } }
                if (s.mode != IslandMode.NOTCH) {
                    IntSlider("Move down", s.expandedOffsetYDp, 0..80) { v ->
                        update { copy(expandedOffsetYDp = v) }
                    }
                }
            }

            // ------------------------------------------------------------ behaviour
            SettingsCard(title = "Behaviour", subtitle = "Timing and what it shows") {
                FloatSlider(
                    label = "Hide after",
                    value = s.hideDelaySec,
                    range = 1f..15f,
                    format = { "%.1f s".format(it) },
                ) { v -> update { copy(hideDelaySec = (v * 2).roundToInt() / 2f) } }
                FloatSlider(
                    label = "Animation speed",
                    value = s.animationSpeed,
                    range = 0.5f..2.5f,
                    format = { "%.1fx".format(it) },
                ) { v -> update { copy(animationSpeed = (v * 10).roundToInt() / 10f) } }

                HorizontalDivider()
                SubLabel("Show")
                SwitchRow("Notifications", s.showNotifications) { v ->
                    update { copy(showNotifications = v) }
                }
                SwitchRow("Media", s.showMedia) { v -> update { copy(showMedia = v) } }
                SwitchRow("Charging", s.showCharging) { v -> update { copy(showCharging = v) } }
                SwitchRow(
                    label = "Hide in landscape",
                    checked = s.hideInLandscape,
                    subtitle = "Keeps it out of the way in videos and games",
                ) { v -> update { copy(hideInLandscape = v) } }
            }

            // ------------------------------------------------------------ colors
            SettingsCard(title = "Colors", subtitle = "Background, text and accent") {
                ColorRow("Background", s.backgroundColor) { c -> update { copy(backgroundColor = c) } }
                ColorRow("Text", s.textColor) { c -> update { copy(textColor = c) } }
                ColorRow("Accent", s.accentColor) { c -> update { copy(accentColor = c) } }
            }

            // ------------------------------------------------------------ permissions
            SettingsCard(title = "Permissions", subtitle = "Review what the island can access") {
                Text(
                    "Tap the idle shape on screen to bring the island back, or long press it " +
                            "to open this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
                    Text("Open permission setup")
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        PreviewBar()
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun IslandHeader(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Dynamic Island", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (enabled) "Running" else "Turned off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

/** Pinned under the scrolling settings so you can re-trigger the island while tuning. */
@Composable
private fun PreviewBar() {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Preview", style = MaterialTheme.typography.labelLarge)
            FilledTonalButton(
                onClick = {
                    IslandBus.post(
                        IslandContent.NotificationItem(
                            key = "test",
                            appName = "Messages",
                            title = "Alex",
                            text = "Are we still on for tonight?",
                            icon = null,
                            tintIcon = false,
                            intent = null,
                            packageName = context.packageName,
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Alert", maxLines = 1) }
            FilledTonalButton(
                onClick = { IslandBus.post(IslandContent.Media("Midnight City", "M83", null, true, context.packageName)) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Media", maxLines = 1) }
            FilledTonalButton(
                onClick = { IslandBus.post(IslandContent.Charging(72, true, "Charging")) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Charge", maxLines = 1) }
        }
    }
}