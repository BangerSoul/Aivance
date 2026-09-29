package com.bangersoul.aivance.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.datastore.ThemeConfig
import com.bangersoul.aivance.core.designsystem.components.AivanceTopBar
import com.bangersoul.aivance.core.designsystem.components.DashboardCard
import com.bangersoul.aivance.core.designsystem.theme.AccentPalettes
import com.bangersoul.aivance.core.designsystem.theme.AccentSeed
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AivanceTopBar(
            title = stringResource(R.string.appearance_title),
            backContentDescription = stringResource(R.string.back),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ThemeModeSection(uiState, viewModel) }
            item { AccentSection(uiState, viewModel) }
            item { DynamicColorSection(uiState, viewModel) }
        }
    }
}

@Composable
private fun ThemeModeSection(state: AppearanceUiState, viewModel: AppearanceViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.theme_mode))
        DashboardCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeConfig.entries.forEach { config ->
                    SelectableRow(
                        label = when (config) {
                            ThemeConfig.FOLLOW_SYSTEM -> stringResource(R.string.theme_follow_system)
                            ThemeConfig.LIGHT -> stringResource(R.string.theme_light)
                            ThemeConfig.DARK -> stringResource(R.string.theme_dark)
                            ThemeConfig.AMOLED -> stringResource(R.string.theme_amoled)
                        },
                        selected = state.themeConfig == config,
                        onClick = { viewModel.setThemeConfig(config) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSection(state: AppearanceUiState, viewModel: AppearanceViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.accent_color))
        DashboardCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AccentSeed.entries.forEach { seed ->
                    AccentSwatch(
                        seed = seed,
                        selected = state.accentSeed == seed.name,
                        onClick = { viewModel.setAccentSeed(seed.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(seed: AccentSeed, selected: Boolean, onClick: () -> Unit) {
    val color = AccentPalettes.light(seed).primary
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun DynamicColorSection(state: AppearanceUiState, viewModel: AppearanceViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.material_you))
        DashboardCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.dynamic_color_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            shape = AivanceTheme.shapes.small,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.width(8.dp))
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AppearanceScreenPreview() {
    AivanceTheme(darkTheme = true) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel(stringResource(R.string.theme_mode))
            DashboardCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeConfig.entries.forEach { config ->
                        SelectableRow(
                            label = config.name,
                            selected = config == ThemeConfig.DARK,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}
