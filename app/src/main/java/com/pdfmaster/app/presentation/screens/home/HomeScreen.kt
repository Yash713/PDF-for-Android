package com.pdfmaster.app.presentation.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pdfmaster.app.presentation.components.FeatureCard
import com.pdfmaster.app.presentation.navigation.Screen
import com.pdfmaster.app.presentation.navigation.homeSections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onFeatureClick: (Screen) -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PDF Master") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.padding(innerPadding)
        ) {
            homeSections.forEach { section ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                items(section.features, span = { GridItemSpan(1) }) { feature ->
                    FeatureCard(
                        title = feature.title,
                        icon = feature.icon,
                        modifier = Modifier.padding(6.dp),
                        onClick = { onFeatureClick(feature) }
                    )
                }
            }
        }
    }
}
