package dev.sriyansh.buzzx9

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.sriyansh.buzzx9.audio.EqRepo
import dev.sriyansh.buzzx9.bt.BtMonitor
import dev.sriyansh.buzzx9.service.EqService
import dev.sriyansh.buzzx9.ui.BuzzTheme
import dev.sriyansh.buzzx9.ui.DeviceScreen
import dev.sriyansh.buzzx9.ui.EqScreen
import dev.sriyansh.buzzx9.ui.ProbeScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        BtMonitor.refreshPermission(this)
        BtMonitor.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EqRepo.init(this)
        BtMonitor.start(this)
        requestPermissions()

        // If the user left the EQ on, make sure the effect chain is actually held open.
        if (EqRepo.enabled) EqService.start(this)

        setContent {
            BuzzTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BtMonitor.refresh()
    }

    private fun requestPermissions() {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wanted += Manifest.permission.BLUETOOTH_CONNECT
            wanted += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun AppRoot() {
    val tabs = remember {
        listOf(
            Tab("Equalizer", Icons.Filled.Tune),
            Tab("Device", Icons.Filled.Headphones),
            Tab("Probe", Icons.Filled.Science)
        )
    }
    var selected by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { inner ->
        Column(Modifier.padding(inner)) {
            when (selected) {
                0 -> EqScreen()
                1 -> DeviceScreen()
                else -> ProbeScreen()
            }
        }
    }
}
