package com.pdfmaster.app.presentation.screens.watermark

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Add") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Remove") })
        }
        Column(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> AddWatermarkScreen(onBack = onBack)
                else -> RemoveWatermarkScreen(onBack = onBack)
            }
        }
    }
}
