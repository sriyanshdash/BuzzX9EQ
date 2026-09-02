package dev.sriyansh.buzzx9.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.sriyansh.buzzx9.audio.TestTone
import dev.sriyansh.buzzx9.bt.GattProbe

@Composable
fun ProbeScreen() {
    val ctx = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            TestTone.stop()
            GattProbe.stopScan(ctx)
            GattProbe.disconnect()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToneCard()
        OffloadCard()
        ScanCard()
        LogCard()
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The five-second answer to "is the equalizer actually doing anything over Bluetooth?"
 */
@Composable
private fun ToneCard() {
    var playing by remember { mutableStateOf<Float?>(null) }

    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("Is the EQ reaching your earbuds?")
            Spacer(Modifier.height(6.dp))
            Text(
                "Put the buds in, start a tone, then go to the Equalizer tab and drag the " +
                    "matching band from -12 to +12 dB. If the loudness does not change, the " +
                    "effect is not in the Bluetooth path and the note below applies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in listOf(62f, 1000f, 8000f)) {
                    val on = playing == f
                    if (on) {
                        Button(onClick = {
                            TestTone.stop()
                            playing = null
                        }) { Text("Stop " + label(f)) }
                    } else {
                        OutlinedButton(onClick = {
                            TestTone.start(f)
                            playing = f
                        }) { Text(label(f)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tone plays at -12 dBFS. Turn your volume down first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun label(f: Float) = if (f >= 1000f) "${(f / 1000f).toInt()} kHz" else "${f.toInt()} Hz"

@Composable
private fun OffloadCard() {
    InfoCard(
        "If the tone does not respond, your phone is almost certainly running Bluetooth " +
            "A2DP hardware offload, which routes audio around the software effects chain. " +
            "Turn it off in Settings > System > Developer options > \"Disable Bluetooth " +
            "A2DP hardware offloading\", then reboot. Battery drain rises slightly. " +
            "This is a platform limitation, not something the app can work around."
    )
}

@Composable
private fun ScanCard() {
    val ctx = LocalContext.current

    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("Protocol probe")
            Spacer(Modifier.height(6.dp))
            Text(
                "Reads what the earbuds advertise and nothing else. The point is to find " +
                    "out whether the chipset exposes a vendor control channel such as " +
                    "JieLi RCSP. If one shows up, real on-device EQ and gesture remapping " +
                    "become possible to build. If nothing does, phone-side EQ is the ceiling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { GattProbe.dumpBondedDevices(ctx) }
                ) { Text("Dump paired (SDP)") }

                if (GattProbe.scanning) {
                    Button(onClick = { GattProbe.stopScan(ctx) }) { Text("Stop scan") }
                } else {
                    OutlinedButton(onClick = { GattProbe.startScan(ctx) }) { Text("BLE scan") }
                }
            }

            if (GattProbe.results.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "${GattProbe.results.size} BLE device(s). Tap one to dump its GATT table.",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                for (r in GattProbe.results.sortedByDescending { it.rssi }) {
                    val interesting = GattProbe.looksControllable(r)
                    TextButton(
                        onClick = { GattProbe.dumpGatt(ctx, r.address) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (r.name ?: "(no name)") + if (interesting) "  *" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (interesting) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                                Text(
                                    r.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${r.rssi} dBm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            GattProbe.connectingTo?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connected to $it, dumping...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LogCard() {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()

    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Probe log",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { copyToClipboard(ctx, GattProbe.logAsText()) }) {
                    Text("Copy")
                }
                TextButton(onClick = { GattProbe.clear() }) { Text("Clear") }
            }

            if (GattProbe.log.isEmpty()) {
                Text(
                    "Nothing yet. Start with \"Dump paired (SDP)\" while the buds are " +
                        "connected: it is the fastest way to see whether an SPP channel exists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 400.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(scroll)
                        .padding(10.dp)
                ) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Text(
                            GattProbe.log.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Buzz X9 probe log", text))
}
