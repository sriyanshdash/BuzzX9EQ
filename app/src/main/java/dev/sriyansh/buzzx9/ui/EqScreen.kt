package dev.sriyansh.buzzx9.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sriyansh.buzzx9.audio.Backend
import dev.sriyansh.buzzx9.audio.Bands
import dev.sriyansh.buzzx9.audio.EqRepo
import dev.sriyansh.buzzx9.audio.IsolationStrength
import dev.sriyansh.buzzx9.audio.Preset
import dev.sriyansh.buzzx9.audio.Presets
import dev.sriyansh.buzzx9.service.EqService
import kotlin.math.abs
import kotlin.math.roundToInt

private val LABEL_WIDTH = 66.dp

@Composable
fun EqScreen() {
    val ctx = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MasterCard()
        CurveCard()
        PresetRow()
        BandsCard()
        IsolationCard()
        PreampCard()

        TextButton(onClick = {
            Presets.byName("Flat")?.let { EqRepo.applyPreset(it) }
            EqService.refresh(ctx)
        }) {
            Text("Reset to flat")
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MasterCard() {
    val ctx = LocalContext.current
    val status = EqService.status
    val backend = status?.globalBackend ?: Backend.NONE

    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Equalizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        statusLine(backend, status?.sessionCount ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = EqRepo.enabled,
                    onCheckedChange = { on ->
                        EqRepo.setEnabled(on)
                        if (on) EqService.start(ctx) else EqService.stop(ctx)
                    }
                )
            }

            if (EqRepo.enabled && backend == Backend.NONE) {
                Spacer(Modifier.height(10.dp))
                Warning(
                    "Android refused to attach an effect to the output mix. " +
                        (status?.globalError ?: "No further detail from the audio HAL.") +
                        " The Probe tab explains what to try."
                )
            } else if (EqRepo.enabled && !EqService.active) {
                Spacer(Modifier.height(10.dp))
                Warning(
                    "Armed but idle. Auto-arm is waiting for " +
                        (EqRepo.boundName ?: "the bound device") + " to connect."
                )
            }
        }
    }
}

private fun statusLine(backend: Backend, sessions: Int): String = when (backend) {
    Backend.DYNAMICS_PROCESSING ->
        "DynamicsProcessing, 10 bands, $sessions session(s) hooked"
    Backend.LEGACY_EQUALIZER ->
        "Fallback: HAL equalizer, bands resampled, $sessions session(s)"
    Backend.NONE ->
        if (sessions == 0) "Not attached" else "Attach failed"
}

@Composable
private fun Warning(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** Straight-line rendering of the current curve. Not a simulated transfer function. */
@Composable
private fun CurveCard() {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline
    val fillTop = line.copy(alpha = 0.25f)

    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val w = size.width
            val h = size.height
            val mid = h / 2f
            val pxPerDb = mid / Bands.MAX_DB

            drawLine(grid, Offset(0f, mid), Offset(w, mid), strokeWidth = 1.5f)
            for (db in intArrayOf(-6, 6)) {
                val y = mid - db * pxPerDb
                drawLine(grid.copy(alpha = 0.4f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            val n = Bands.COUNT
            val step = w / (n - 1).toFloat()
            val pts = (0 until n).map { i ->
                Offset(i * step, mid - EqRepo.gains[i] * pxPerDb)
            }

            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until n) {
                    val prev = pts[i - 1]
                    val cur = pts[i]
                    val cx = (prev.x + cur.x) / 2f
                    cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
                }
            }
            drawPath(path, line, style = Stroke(width = 3f))

            val fill = Path().apply {
                addPath(path)
                lineTo(w, mid)
                lineTo(0f, mid)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(fillTop, Color.Transparent, fillTop)))

            for (p in pts) drawCircle(line, radius = 4f, center = p)
        }
    }
}

// -------------------------------------------------------------------------- presets

@Composable
private fun PresetRow() {
    val ctx = LocalContext.current
    val active = Presets.byName(EqRepo.presetName)
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Preset?>(null) }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (p in Presets.all) {
                val selected = p.name == EqRepo.presetName
                AssistChip(
                    onClick = {
                        EqRepo.applyPreset(p)
                        EqService.refresh(ctx)
                    },
                    label = { Text(if (p.userDefined) "★ " + p.name else p.name) },
                    colors = if (selected) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                )
            }
            AssistChip(
                onClick = { showSaveDialog = true },
                label = { Text("+ Save current") },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.secondary
                )
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                active?.note ?: "Your own curve, not saved yet.",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (active != null && active.userDefined) {
                TextButton(onClick = { pendingDelete = active }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(onDismiss = { showSaveDialog = false })
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"" + target.name + "\"?") },
            text = { Text("The curve stays loaded; only the saved preset goes away.") },
            confirmButton = {
                TextButton(onClick = {
                    EqRepo.deletePreset(target)
                    pendingDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val clean = Presets.sanitize(name)
    val overwrites = clean.isNotEmpty() && Presets.isNameTaken(clean)
    val isBuiltIn = Presets.BUILT_IN.any { it.name.equals(clean, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save current curve") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Preset name") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        isBuiltIn -> "That name belongs to a built-in preset. Pick another."
                        overwrites -> "Replaces your existing preset with this name."
                        else -> "Saved presets appear in the row above, marked with a star."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBuiltIn) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = clean.isNotEmpty() && !isBuiltIn,
                onClick = {
                    EqRepo.saveCurrentAsPreset(clean)
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------- bands

@Composable
private fun BandsCard() {
    val ctx = LocalContext.current

    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Frequency bands",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { EqRepo.setBandGuide(!EqRepo.bandGuide) }) {
                    Text(if (EqRepo.bandGuide) "Hide guide" else "What do these do?")
                }
            }

            for (i in 0 until Bands.COUNT) {
                BandRow(i, EqRepo.bandGuide) { db ->
                    EqRepo.setGain(i, db)
                    EqService.refresh(ctx)
                }
                if (i < Bands.COUNT - 1) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BandRow(index: Int, showGuide: Boolean, onChange: (Float) -> Unit) {
    val gain = EqRepo.gains[index]

    Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(LABEL_WIDTH),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    Bands.LABELS[index],
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    Bands.NAMES[index],
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = gain,
                onValueChange = onChange,
                valueRange = Bands.MIN_DB..Bands.MAX_DB,
                steps = 47, // half-decibel detents across the 24 dB span
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            Text(
                formatDb(gain),
                modifier = Modifier.width(52.dp),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                color = if (abs(gain) < 0.05f) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }

        if (showGuide) {
            Text(
                Bands.DESCRIPTIONS[index],
                Modifier.padding(start = LABEL_WIDTH + 10.dp, end = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ------------------------------------------------------------------------ isolation

@Composable
private fun IsolationCard() {
    val ctx = LocalContext.current
    val status = EqService.status
    // Only trust an unsupported verdict once the engine has actually attached.
    val unsupported = status != null && !status.isolationSupported

    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Isolation mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "For listening in noisy places",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = EqRepo.isolation && !unsupported,
                    enabled = !unsupported,
                    onCheckedChange = {
                        EqRepo.setIsolation(it)
                        EqService.refresh(ctx)
                    }
                )
            }

            if (unsupported) {
                Spacer(Modifier.height(10.dp))
                Warning(
                    "This phone's audio HAL would not give us a compressor stage, so " +
                        "Isolation mode cannot run. The equalizer is unaffected."
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Compresses each frequency band separately, so quiet passages rise above " +
                    "traffic and cabin noise while loud parts stay where they are. Your EQ " +
                    "curve is untouched, so the music keeps its tonal balance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (EqRepo.isolation && !unsupported) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in IsolationStrength.entries) {
                        FilterChip(
                            selected = EqRepo.isolationStrength == s,
                            onClick = {
                                EqRepo.setIsolationStrength(s)
                                EqService.refresh(ctx)
                            },
                            label = { Text(s.label) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    EqRepo.isolationStrength.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    "This is not noise cancellation. The ENC on the Buzz X9 cleans up your " +
                        "voice for whoever you are calling; it changes nothing about what " +
                        "you hear, and it only runs during calls. These buds have no ANC, " +
                        "and no app can add it. What actually blocks noise here is the ear " +
                        "tip seal, so try the other tip sizes in the box.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --------------------------------------------------------------------------- preamp

@Composable
private fun PreampCard() {
    val ctx = LocalContext.current
    val effective = EqRepo.effectivePreamp()

    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pre-amp",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatDb(effective),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Boosting bands without cutting the input clips the mixer. Auto keeps " +
                    "headroom equal to your largest boost, plus whatever Isolation mode adds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = EqRepo.autoPreamp,
                    onCheckedChange = {
                        EqRepo.setAutoPreamp(it)
                        EqService.refresh(ctx)
                    }
                )
                Text("Automatic headroom", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = if (EqRepo.autoPreamp) effective else EqRepo.preamp,
                onValueChange = {
                    EqRepo.setPreamp(it)
                    EqService.refresh(ctx)
                },
                valueRange = -12f..0f,
                steps = 23,
                enabled = !EqRepo.autoPreamp
            )
        }
    }
}

private fun formatDb(v: Float): String {
    val rounded = (v * 2f).roundToInt() / 2f
    val sign = if (rounded > 0f) "+" else ""
    return sign + (if (rounded == rounded.toInt().toFloat()) {
        rounded.toInt().toString()
    } else {
        String.format("%.1f", rounded)
    }) + " dB"
}
