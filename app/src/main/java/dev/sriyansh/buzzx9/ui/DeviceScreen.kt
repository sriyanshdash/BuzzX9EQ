package dev.sriyansh.buzzx9.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sriyansh.buzzx9.audio.EqRepo
import dev.sriyansh.buzzx9.bt.BatteryState
import dev.sriyansh.buzzx9.bt.BtDeviceInfo
import dev.sriyansh.buzzx9.bt.BtMonitor
import dev.sriyansh.buzzx9.bt.CellState
import dev.sriyansh.buzzx9.service.EqService

@Composable
fun DeviceScreen() {
    val ctx = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("Connected audio devices")

        if (!BtMonitor.hasPermission) {
            InfoCard(
                "Bluetooth permission not granted. Without it Android will not say which " +
                    "device is playing, so auto-arm cannot work. Grant it in " +
                    "Settings > Apps > Buzz X9 EQ > Permissions."
            )
        } else if (!BtMonitor.adapterOn) {
            InfoCard("Bluetooth is off.")
        } else if (BtMonitor.connected.isEmpty()) {
            InfoCard("No audio device connected right now.")
        } else {
            for (d in BtMonitor.connected) {
                DeviceCard(d)
            }
        }

        TextButton(onClick = { BtMonitor.refresh() }) { Text("Refresh") }

        SectionTitle("Auto-arm")
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only equalize the bound device", fontWeight = FontWeight.Medium)
                        Text(
                            EqRepo.boundName?.let { "Bound to $it" }
                                ?: "Nothing bound yet, so the curve applies to all output.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = EqRepo.autoArm,
                        onCheckedChange = {
                            EqRepo.setAutoArm(it)
                            EqService.refresh(ctx)
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "With this on, the curve goes live when the bound earbuds connect and " +
                        "goes flat when they disconnect, so your phone speaker stays untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionTitle("Touch controls (from the manual)")
        GestureCard()

        Spacer(Modifier.height(24.dp))
    }
}

// ----------------------------------------------------------------------- device card

@Composable
private fun DeviceCard(d: BtDeviceInfo) {
    val ctx = LocalContext.current
    val bound = EqRepo.boundAddress == d.address

    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        d.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        d.address,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (bound) {
                    Tag("BOUND", MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (d.mediaConnected) Tag("Media", MaterialTheme.colorScheme.secondary)
                if (d.callsConnected) Tag("Calls", MaterialTheme.colorScheme.secondary)
            }

            Spacer(Modifier.height(14.dp))
            BatteryBlock(d.battery)

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    if (bound) EqRepo.bindDevice(null, null)
                    else EqRepo.bindDevice(d.address, d.name)
                    BtMonitor.refresh()
                    EqService.refresh(ctx)
                }
            ) {
                Text(if (bound) "Unbind" else "Bind as my Buzz X9")
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// --------------------------------------------------------------------------- battery

@Composable
private fun BatteryBlock(state: BatteryState) {
    var showWhy by remember { mutableStateOf(false) }

    Column {
        Text(
            "Charge",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        when {
            state.perBudKnown -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BatteryTile("Left", state.left, Modifier.weight(1f))
                    BatteryTile("Right", state.right, Modifier.weight(1f))
                    BatteryTile("Case", state.case, Modifier.weight(1f))
                }
            }

            state.combined.known -> {
                BatteryTile("Both earbuds", state.combined, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your earbuds report one figure for the pair, not left and right " +
                        "separately. Per-earbud readings need a vendor pairing protocol " +
                        "these buds do not implement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        "Not reported. Android has no public API for earbud charge, and " +
                            "every hidden route was refused or came back empty. If the " +
                            "system Bluetooth settings shows a figure and this does not, " +
                            "the platform is withholding it from ordinary apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { showWhy = !showWhy }) {
            Text(
                if (showWhy) "Hide what was tried" else "What was tried?",
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (showWhy) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(10.dp)
            ) {
                if (state.sourceLog.isEmpty()) {
                    Text(
                        "Every source refused silently.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    for (l in state.sourceLog) {
                        Text(
                            l,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "A BLE Battery Service read from the Probe tab also feeds this panel.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BatteryTile(label: String, cell: CellState, modifier: Modifier = Modifier) {
    val pct = cell.percent
    val tint = when {
        pct == null -> MaterialTheme.colorScheme.onSurfaceVariant
        pct <= 15 -> MaterialTheme.colorScheme.error
        pct <= 35 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(
            label + if (cell.charging == true) "  ⚡" else "",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            pct?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
        if (pct != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = tint,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
    }
}

// -------------------------------------------------------------------------- gestures

@Composable
private fun GestureCard() {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            val rows = listOf(
                "Power on / off" to "Long press 5 s",
                "Answer / hang up" to "Single click",
                "Reject call" to "Double click",
                "Play / pause" to "Double click (L or R)",
                "Next track" to "Triple click (R)",
                "Previous track" to "Triple click (L)",
                "Volume up" to "Single click (R)",
                "Volume down" to "Single click (L)",
                "Game / music mode" to "Quad click (L or R)",
                "Voice assistant" to "Five clicks"
            )
            rows.forEachIndexed { i, (action, gesture) ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(action, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        gesture,
                        Modifier.width(140.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (i < rows.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "These run inside the earbud firmware. No app can remap them unless the " +
                    "buds turn out to expose a vendor control channel; see the Probe tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------- shared

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
