@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val prefs = getSharedPreferences("mora", Context.MODE_PRIVATE)
        if (prefs.getBoolean("service", true)) {
            try {
                startForegroundService(Intent(this, EventService::class.java))
            } catch (e: Exception) {
                Store.message = "Pemantau gagal dimulai: " + (e.message ?: "?")
            }
        }
        setContent { MoraTheme { MoraApp() } }
    }

    override fun onResume() {
        super.onResume()
        Store.refresh(applicationContext)
        Store.startPolling(applicationContext)
    }

    override fun onPause() {
        super.onPause()
        Store.stopPolling()
    }
}

@Composable
fun MoraApp() {
    var tab by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val msg = Store.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snack.showSnackbar(msg)
            Store.message = null
        }
    }
    val titles = listOf("Beranda", "Aktivitas", "Aplikasi", "Pengaturan", "Alat")
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mora Shield", fontWeight = FontWeight.Bold)
                        Text(titles[tab], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { Store.refresh(Store.appCtx) }) { Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val icons = listOf(Icons.Filled.Home, Icons.Filled.Notifications, Icons.Filled.List, Icons.Filled.Settings, androidx.compose.material.icons.Icons.Filled.Build)
                for (i in titles.indices) {
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icons[i], contentDescription = titles[i]) },
                        label = { Text(titles[i], maxLines = 1) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (Store.busy) LinearProgressIndicator(Modifier)
            when (tab) {
                0 -> HomeScreen()
                1 -> ActivityScreen()
                2 -> AppsScreen()
                3 -> SettingsScreen()
                else -> ToolsScreen()
            }
        }
    }
    if (Store.textDialogOpen) {
        AlertDialog(
            onDismissRequest = { Store.textDialogOpen = false },
            title = { Text(Store.textDialogTitle) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(Store.textDialogBody, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { Store.textDialogOpen = false }) { Text("Tutup") } }
        )
    }
}
