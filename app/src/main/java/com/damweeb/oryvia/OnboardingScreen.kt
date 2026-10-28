package com.damweeb.oryvia

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    var overlay by remember { mutableStateOf(Perms.overlay(context)) }
    var listener by remember { mutableStateOf(Perms.notificationAccess(context)) }
    var battery by remember { mutableStateOf(Perms.batteryExempt(context)) }
    var posting by remember { mutableStateOf(Perms.postNotifications(context)) }

    val postLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> posting = granted }

    // The system settings pages return no result, so re-check everything whenever we come back.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlay = Perms.overlay(context)
                listener = Perms.notificationAccess(context)
                battery = Perms.batteryExempt(context)
                posting = Perms.postNotifications(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Only permissions that can actually be detected count towards the progress bar.
    val tracked = buildList {
        add(overlay)
        add(listener)
        add(battery)
        if (Build.VERSION.SDK_INT >= 33) add(posting)
    }
    val doneCount = tracked.count { it }
    val progress by animateFloatAsState(doneCount / tracked.size.toFloat(), label = "progress")
    val requiredDone = overlay && listener

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroPill()

            Text("Set up your island", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A few permissions let the island draw on screen and read notifications. " +
                        "Tap Grant, switch it on, then come back. Checkmarks update on their own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
            )
            Text(
                "$doneCount of ${tracked.size} granted",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            var number = 1

            PermissionStep(
                number = number++,
                title = "Display over other apps",
                description = "Lets the island draw on top of every other app.",
                granted = overlay,
                required = true,
                buttonLabel = "Grant",
                onClick = { context.openOverlaySettings() },
            )
            PermissionStep(
                number = number++,
                title = "Notification access",
                description = "Lets the island read notifications and the current media session. " +
                        "Everything stays on your phone.",
                granted = listener,
                required = true,
                buttonLabel = "Grant",
                onClick = { context.openNotificationAccessSettings() },
            )
            PermissionStep(
                number = number++,
                title = "Unrestricted battery",
                description = "Stops Android from killing the island in the background.",
                granted = battery,
                required = false,
                buttonLabel = "Allow",
                onClick = { context.requestBatteryExemption() },
            )
            if (Perms.isColorOsFamily()) {
                PermissionStep(
                    number = number++,
                    title = "Auto-start (realme / ColorOS)",
                    description = "Turn on auto-start for this app so the island survives " +
                            "a reboot and background cleaning. This can't be detected, " +
                            "so there is no checkmark.",
                    granted = null,
                    required = false,
                    buttonLabel = "Open",
                    onClick = { context.openAutoStartSettings() },
                )
            }
            if (Build.VERSION.SDK_INT >= 33) {
                PermissionStep(
                    number = number++,
                    title = "Show notifications",
                    description = "Lets the small \"island is running\" notification appear.",
                    granted = posting,
                    required = false,
                    buttonLabel = "Allow",
                    onClick = { postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        // Pinned so the button is always reachable without scrolling.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Button(
                onClick = onDone,
                enabled = requiredDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
            ) {
                Text(if (requiredDone) "Continue" else "Grant the required permissions first")
            }
        }
    }
}

/** A black pill that breathes in and out, a hint of what the island will do. */
@Composable
private fun HeroPill() {
    val transition = rememberInfiniteTransition(label = "hero")
    val widthDp by transition.animateFloat(
        initialValue = 56f,
        targetValue = 160f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heroWidth",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(widthDp.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        )
    }
}

/**
 * One permission as a numbered card. [granted] = null means it can't be detected,
 * so only the button is shown and the card never turns green.
 */
@Composable
private fun PermissionStep(
    number: Int,
    title: String,
    description: String,
    granted: Boolean?,
    required: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    val done = granted == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (done) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    CheckMark(Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("$number", style = MaterialTheme.typography.labelLarge)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (required) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Required",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (done) {
                    Text(
                        "Granted",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(onClick = onClick) { Text(buttonLabel) }
                }
            }
        }
    }
}

@Composable
private fun CheckMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()
        drawLine(color, Offset(w * 0.15f, h * 0.55f), Offset(w * 0.4f, h * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, h * 0.8f), Offset(w * 0.85f, h * 0.25f), stroke, StrokeCap.Round)
    }
}