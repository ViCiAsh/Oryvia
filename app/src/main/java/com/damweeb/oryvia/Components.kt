package com.damweeb.oryvia

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ---------------------------------------------------------------- theme

/** Uses the phone's Material You colors on Android 12+, a plain dark scheme below that. */
@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

// ---------------------------------------------------------------- cards

/** A rounded card with a tappable header that expands and collapses its content. */
@Composable
fun SettingsCard(
    title: String,
    subtitle: String? = null,
    defaultOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(defaultOpen) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Chevron(
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = open) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun Chevron(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(w * 0.15f, h * 0.35f), Offset(w * 0.5f, h * 0.7f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.85f, h * 0.35f), stroke, StrokeCap.Round)
    }
}

/** Small colored label used to split a card into sub-sections. */
@Composable
fun SubLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

// ---------------------------------------------------------------- rows

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String = "dp",
    onChange: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            ValueChip("$value $unit")
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
fun FloatSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            ValueChip(format(value))
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

// ---------------------------------------------------------------- mode selector

/** Three selectable cards, each with a tiny drawing of how that mode looks at the top of a phone. */
@Composable
fun ModeSelector(selected: IslandMode, onSelect: (IslandMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IslandMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModePreview(
                    mode = mode,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(mode.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ModePreview(mode: IslandMode, color: Color) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when (mode) {
            IslandMode.NOTCH -> Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(28.dp, 12.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(color)
            )
            IslandMode.PILL -> Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 5.dp)
                    .size(28.dp, 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            IslandMode.PUNCH_HOLE -> Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 4.dp)
                    .size(26.dp, 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

// ---------------------------------------------------------------- color picker

private val PRESETS = listOf(
    0xFF000000L, 0xFFFFFFFFL, 0xFF1C1C1EL, 0xFFFF3B30L, 0xFFFF9500L,
    0xFFFFCC00L, 0xFF4ADE80L, 0xFF0A84FFL, 0xFFBF5AF2L, 0xFFFF375FL,
).map { it.toInt() }

private fun hex(color: Int): String = String.format("%08X", color)

private fun parseHex(t: String): Int? = when (t.length) {
    6 -> t.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
    8 -> t.toLongOrNull(16)?.toInt()
    else -> null
}

@Composable
fun ColorRow(label: String, color: Int, onPick: (Int) -> Unit) {
    var text by remember { mutableStateOf(hex(color)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PRESETS.forEach { c ->
                val selected = c == color
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable {
                            text = hex(c)
                            onPick(c)
                        }
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                val clean = t.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                text = clean
                parseHex(clean)?.let(onPick)
            },
            label = { Text("Hex (RRGGBB or AARRGGBB)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}