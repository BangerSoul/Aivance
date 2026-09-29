package com.bangersoul.aivance.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * The standard top bar used across the app. Handles back navigation,
 * title/subtitle, and trailing actions consistently. Both accessible names are
 * caller-supplied: this module ships no string resources, so a hardcoded label
 * here could never be translated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AivanceTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backContentDescription: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        title = {
            if (subtitle != null) {
                androidx.compose.foundation.layout.Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = backContentDescription
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}
