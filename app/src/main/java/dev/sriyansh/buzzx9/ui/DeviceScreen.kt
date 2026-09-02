package dev.sriyansh.buzzx9.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import dev.sriyansh.buzzx9.audio.EqRepo
import dev.sriyansh.buzzx9.bt.BtMonitor
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
            InfoCard("No A2DP device connected right now.")
        } else {
            for (d in BtMonitor.connected) {
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
                            Text(
                                d.batteryPercent?.let { "$it%" } ?: "battery n/a",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val bound = EqRepo.boundAddress == d.address
                        OutlinedButton(
                            onClick = {
                                if (bound) {
                                    EqRepo.bindDevice(null, null)
                                } else {
                                    EqRepo.bindDevice(d.address, d.name)
                                }
                                BtMonitor.refresh()
                                EqService.refresh(ctx)
                            }
                        ) {
                            Text(if (bound) "Unbind" else "Bind as my Buzz X9")
                        }
                    }
                }
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

        SectionTitle("Battery readout")
        InfoCard(
            "Android has no public API for earbud battery. The app asks the Bluetooth " +
                "stack through a hidden method, which newer Android builds are allowed to " +
                "refuse. When it says n/a, the stack refused rather than the buds being " +
                "silent, and the figure in the system Bluetooth settings is the one to trust."
        )

        SectionTitle("Touch controls (from the manual)")
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

        Spacer(Modifier.height(24.dp))
    }
}

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
