package com.kalorientracker.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.usecase.PortionScale
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Motion
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.motionSpec

private val TabularNumbers = "tnum"

/** A number that counts smoothly to its new value. */
@Composable
fun AnimatedNumber(
    value: Double,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Palette.TextPrimary,
    format: (Double) -> String = { Fmt.int(it) },
) {
    val animated by animateFloatAsState(value.toFloat(), motionSpec(Motion.gentle()), label = "number")
    Text(
        text = format(animated.toDouble()),
        style = style.copy(fontFeatureSettings = TabularNumbers),
        color = color,
        modifier = modifier,
    )
}

/** Uppercase monospaced section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextTertiary) {
    Text(text.uppercase(Fmt.locale), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/**
 * Central calorie meter: a 270° arc. Past the target the arc stays full and the surplus is shown
 * as a thin second lap, without judgement.
 */
@Composable
fun CalorieRing(
    consumed: Double,
    target: Double,
    modifier: Modifier = Modifier,
    diameter: Dp = 248.dp,
    strokeWidth: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    val fraction = if (target > 0) (consumed / target).toFloat() else 0f
    val progress by animateFloatAsState(fraction.coerceIn(0f, 1f), motionSpec(Motion.gentle()), label = "ring")
    val overflow by animateFloatAsState((fraction - 1f).coerceIn(0f, 1f), motionSpec(Motion.gentle()), label = "ringOverflow")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(Palette.Track, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress > 0f) {
                drawArc(Palette.TextPrimary, START, SWEEP * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (overflow > 0f) {
                val thin = stroke * 0.3f
                val o = inset + stroke * 1.4f
                drawArc(
                    Palette.TextSecondary, START, SWEEP * overflow, false,
                    Offset(o, o), Size(size.width - 2 * o, size.height - 2 * o),
                    style = Stroke(thin, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

private const val START = 135f
private const val SWEEP = 270f

/** Label, "current / target unit" and a thin bar. Color marks identity; text stays in text ink. */
@Composable
fun MacroBar(
    label: String,
    current: Double,
    target: Double,
    color: Color,
    unit: String = "g",
    modifier: Modifier = Modifier,
) {
    val fraction = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(fraction, motionSpec(Motion.gentle()), label = "macro")
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
            AnimatedNumber(current, MaterialTheme.typography.titleSmall)
            Text(" / ${Fmt.int(target)} $unit", style = MaterialTheme.typography.bodyMedium, color = Palette.TextTertiary)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val r = size.height / 2
            drawRoundRect(Palette.Track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            if (animated > 0f) {
                drawRoundRect(
                    color,
                    size = Size(size.width * animated, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            }
        }
    }
}

@Composable
fun ConfidenceBadge(confidence: Confidence, modifier: Modifier = Modifier) {
    val filled = when (confidence) {
        Confidence.HIGH -> 3
        Confidence.MEDIUM -> 2
        Confidence.LOW -> 1
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                Modifier
                    .padding(end = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) Palette.TextPrimary else Palette.OutlineStrong),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            confidence.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (confidence == Confidence.LOW) Palette.TextPrimary else Palette.TextTertiary,
        )
    }
}

@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, motionSpec(Motion.snappy()), label = "press")
    return scale
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    haptic: HapticEvent = HapticEvent.Tap,
) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .scale(pressScale(source))
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (enabled) Palette.TextPrimary else Palette.SurfaceHigh)
            .clickable(interactionSource = source, indication = null, enabled = enabled) {
                haptics.perform(haptic)
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) Palette.Background else Palette.TextTertiary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .scale(pressScale(source))
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, if (enabled) Palette.OutlineStrong else Palette.Outline, RoundedCornerShape(26.dp))
            .clickable(interactionSource = source, indication = null, enabled = enabled) {
                haptics.perform(HapticEvent.Tap)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) Palette.TextPrimary else Palette.TextTertiary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingColor: Color? = null,
) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Palette.TextPrimary else Color.Transparent)
            .border(1.dp, if (selected) Palette.TextPrimary else Palette.OutlineStrong, shape)
            .clickable {
                haptics.perform(HapticEvent.Toggle)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingColor != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(leadingColor))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Background else Palette.TextSecondary)
    }
}

/** Seven-step qualitative portion slider with a haptic tick per step. */
@Composable
fun PortionSlider(
    index: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    var lastIndex by remember { mutableIntStateOf(index) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Portion", Modifier.weight(1f))
            Text(PortionScale.label(index), style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { raw ->
                val newIndex = raw.toInt().coerceIn(PortionScale.steps.indices)
                if (newIndex != lastIndex) {
                    lastIndex = newIndex
                    haptics.perform(HapticEvent.Tick)
                    onIndexChange(newIndex)
                }
            },
            valueRange = 0f..(PortionScale.steps.size - 1).toFloat(),
            steps = PortionScale.steps.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = Palette.TextPrimary,
                activeTrackColor = Palette.TextPrimary,
                inactiveTrackColor = Palette.Track,
                activeTickColor = Palette.Background,
                inactiveTickColor = Palette.OutlineStrong,
            ),
        )
        Row {
            Text(PortionScale.steps.first().label, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary, modifier = Modifier.weight(1f))
            Text(PortionScale.steps.last().label, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }) },
        label = { Text(label) },
        suffix = if (suffix != null) { { Text(suffix, color = Palette.TextTertiary) } } else null,
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors = fieldColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    )
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder, color = Palette.TextTertiary) } } else null,
        singleLine = singleLine,
        colors = fieldColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.TextPrimary,
    unfocusedBorderColor = Palette.OutlineStrong,
    focusedLabelColor = Palette.TextPrimary,
    unfocusedLabelColor = Palette.TextTertiary,
    cursorColor = Palette.TextPrimary,
    focusedTextColor = Palette.TextPrimary,
    unfocusedTextColor = Palette.TextPrimary,
)

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                haptics.perform(HapticEvent.Toggle)
                onCheckedChange(!checked)
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.perform(HapticEvent.Toggle)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Background,
                checkedTrackColor = Palette.TextPrimary,
                uncheckedThumbColor = Palette.TextTertiary,
                uncheckedTrackColor = Palette.Surface,
                uncheckedBorderColor = Palette.OutlineStrong,
            ),
        )
    }
}

/** Screen header with optional back arrow and the settings gear. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val haptics = LocalHaptics.current
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = { haptics.perform(HapticEvent.Tap); onBack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = Palette.TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            if (subtitle != null) SectionLabel(subtitle)
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary)
        }
        actions()
        if (onSettings != null) {
            IconButton(onClick = { haptics.perform(HapticEvent.Tap); onSettings() }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen", tint = Palette.TextSecondary)
            }
        }
    }
}

@Composable
fun Expandable(
    expanded: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(content = content)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.TextTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
fun NutrientRow(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Palette.TextPrimary)
        if (detail != null) {
            Text(" $detail", style = MaterialTheme.typography.bodyMedium, color = Palette.TextTertiary)
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp)
