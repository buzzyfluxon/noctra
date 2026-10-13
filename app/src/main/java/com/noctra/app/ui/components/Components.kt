package com.noctra.app.ui.components

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.view.WindowCompat
import com.noctra.app.data.Subject
import com.noctra.app.data.TaskEntity
import com.noctra.app.data.TimeUtil
import com.noctra.app.ui.theme.NC
import com.noctra.app.ui.theme.NoctraTheme

@Composable
fun StatusIcons(lightBackground: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = lightBackground
            c.isAppearanceLightNavigationBars = lightBackground
        }
    }
}

@Composable
fun LightBackdrop(content: @Composable BoxScope.() -> Unit) {
    StatusIcons(true)
    CompositionLocalProvider(LocalContentColor provides NC.Slate) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFE3DCFF), NC.Canvas, NC.Canvas))),
            content = content
        )
    }
}

@Composable
fun DarkBackdrop(content: @Composable BoxScope.() -> Unit) {
    NoctraTheme(dark = true) {
        StatusIcons(false)
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(NC.InkMid, NC.Ink))),
                content = content
            )
        }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        "noctra",
        modifier,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        color = fg,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
fun NcProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    fill: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 8.dp
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(fill)
        )
    }
}

@Composable
fun NcCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val base = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(onClick = onClick, modifier = base, shape = shape, color = container, border = border, tonalElevation = 0.dp) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Surface(modifier = base, shape = shape, color = container, border = border, tonalElevation = 0.dp) {
            Column(Modifier.padding(18.dp), content = content)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null) {
    NcCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

fun subjectColors(s: Subject): Pair<Color, Color> = when (s) {
    Subject.PHYSICS -> Color(0xFFE0D9FF) to Color(0xFF4B36B8)
    Subject.CHEMISTRY -> Color(0xFFFFD9D6) to Color(0xFFB4235A)
    Subject.BIOLOGY -> Color(0xFFCFF3E6) to Color(0xFF0B6B58)
    Subject.CUSTOM -> Color(0xFFFFEBC2) to Color(0xFF8A5A00)
}

fun subjectTint(s: Subject): Color = when (s) {
    Subject.PHYSICS -> Color(0xFFEAE6FF)
    Subject.CHEMISTRY -> Color(0xFFFFEDE8)
    Subject.BIOLOGY -> Color(0xFFE3F7F1)
    Subject.CUSTOM -> Color(0xFFFFF4DB)
}

@Composable
fun SubjectChip(task: TaskEntity, modifier: Modifier = Modifier) {
    val (bg, fg) = subjectColors(task.subject)
    Pill(task.subjectLabel, bg, fg, modifier)
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    )
}

@Composable
fun ProgressDialog(task: TaskEntity, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var p by remember { mutableFloatStateOf(task.progress.toFloat()) }
    var est by remember { mutableStateOf(task.estimateMin.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update progress") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium)
                Slider(value = p, onValueChange = { p = it }, valueRange = 0f..100f, steps = 19)
                Text("${p.toInt()}% done", style = MaterialTheme.typography.labelLarge)
                NumberField(est, { est = it }, "Estimated minutes", Modifier.fillMaxWidth())
                Text(
                    "Completion is only recorded when you confirm it with DONE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(p.toInt(), (est.toIntOrNull() ?: task.estimateMin).coerceAtLeast(5)) }) {
                Text("SAVE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
fun MiniBars(
    values: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    if (v > 0) TimeUtil.duration(v) else "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (v > 0) (88f * v / max).coerceAtLeast(3f).dp else 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (v > 0) color else MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(4.dp))
                Text(labels[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
