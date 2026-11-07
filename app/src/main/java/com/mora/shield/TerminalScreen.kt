package com.mora.shield

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding

@Composable
fun TerminalScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)
    ) {
        PageIntro("Terminal", "Shell Android terpisah dari alat dan dashboard. Mode root memerlukan persetujuan KernelSU/Magisk.")
        TerminalCard()
    }
}
