
@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.text.selection.SelectionContainer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build as BuildIcon

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Meminta izin notifikasi Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }

        // Membaca konfigurasi service
        val prefs = getSharedPreferences("mora", Context.MODE_PRIVATE)
        if (prefs.getBoolean("secure", true)) {
            window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }

        if (prefs.getBoolean("service", true)) {
            try {
                val serviceIntent = Intent(
                    this,
                    EventService::class.java
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

            } catch (e: Exception) {
                Store.message =
                    "Pemantau gagal dimulai: ${e.message ?: "Unknown error"}"
            }
        }

        // Menjalankan UI Material 3
        setContent {
            MoraTheme {
                MoraApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        Store.refresh(applicationContext)
        Store.startPolling(applicationContext)
    }

    override fun onPause() {
        Store.stopPolling()
        super.onPause()
    }
}

// =====================================================
// MORA SHIELD APP
// =====================================================

@Composable
fun MoraApp() {

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    val message = Store.message

    // Menampilkan pesan aplikasi
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            Store.message = null
        }
    }

    val titles = listOf(
        "Beranda",
        "Aktivitas",
        "Aplikasi",
        "Pengaturan",
        "Alat"
    )

    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Notifications,
        Icons.Filled.List,
        Icons.Filled.Settings,
        Icons.Filled.BuildIcon
    )

    Scaffold(

        // ================= TOP BAR =================

        topBar = {
            TopAppBar(

                title = {
                    Column {
                        Text(
                            text = "Mora Shield",
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = titles[selectedTab],
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },

                actions = {
                    IconButton(
                        onClick = {
                            Store.refresh(Store.appCtx)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Muat ulang"
                        )
                    }
                }
            )
        },

        // ================= BOTTOM NAVIGATION =================

        bottomBar = {
            NavigationBar {

                titles.forEachIndexed { index, title ->

                    NavigationBarItem(
                        selected = selectedTab == index,

                        onClick = {
                            selectedTab = index
                        },

                        icon = {
                            Icon(
                                imageVector = icons[index],
                                contentDescription = title
                            )
                        },

                        label = {
                            Text(
                                text = title,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        },

        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState
            )
        }

    ) { innerPadding ->

        Column(
            modifier = Modifier.padding(innerPadding)
        ) {

            // Indikator proses
            if (Store.busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                )
            }

            // ================= SCREEN CONTENT =================

            when (selectedTab) {

                0 -> HomeScreen()

                1 -> ActivityScreen()

                2 -> AppsScreen()

                3 -> SettingsScreen()

                4 -> ToolsScreen()
            }
        }
    }

    // ================= TEXT DIALOG =================

    if (Store.textDialogOpen) {

        AlertDialog(

            onDismissRequest = {
                Store.textDialogOpen = false
            },

            title = {
                Text(Store.textDialogTitle)
            },

            text = {
                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    )
                ) {

                    SelectionContainer {
                        Text(
                            text = Store.textDialogBody,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        Store.textDialogOpen = false
                    }
                ) {
                    Text("Tutup")
                }
            }
        )
    }
}
