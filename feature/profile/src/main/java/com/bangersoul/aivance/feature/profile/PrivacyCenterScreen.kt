package com.bangersoul.aivance.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.designsystem.components.ActionButton
import com.bangersoul.aivance.core.designsystem.components.AivanceScreen
import com.bangersoul.aivance.core.designsystem.components.DashboardCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyCenterScreen(
    viewModel: PrivacyViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AivanceScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PrivacyTip, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.privacy_header), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.privacy_header_sub), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Data Portability
            val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importData(uri)
                }
            }

            DashboardCard {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.privacy_portability), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.privacy_portability_sub), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton(
                            text = stringResource(R.string.export_backup),
                            onClick = { viewModel.exportData() },
                            icon = Icons.Rounded.Download,
                            modifier = Modifier.weight(1f)
                        )
                        ActionButton(
                            text = stringResource(R.string.restore_backup),
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            icon = Icons.Rounded.PrivacyTip,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Danger Zone
            Text(stringResource(R.string.danger_zone), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            DashboardCard {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.delete_all_data), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.delete_all_data_sub), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    ActionButton(
                        text = stringResource(R.string.wipe_everything),
                        onClick = { showDeleteConfirm = true },
                        icon = Icons.Rounded.DeleteForever,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (uiState is PrivacyUiState.Success) {
                Text((uiState as PrivacyUiState.Success).message, color = MaterialTheme.colorScheme.primary)
            } else if (uiState is PrivacyUiState.Error) {
                Text((uiState as PrivacyUiState.Error).message, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (uiState is PrivacyUiState.RequiresPassphrase) {
        val req = uiState as PrivacyUiState.RequiresPassphrase
        var passphraseInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { /* Modal */ },
            title = { Text(stringResource(R.string.privacy_passphrase_required)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (req.isRetry) stringResource(R.string.privacy_passphrase_invalid)
                        else stringResource(R.string.privacy_passphrase_body)
                    )
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text(stringResource(R.string.privacy_passphrase_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = passphraseInput.isNotBlank(),
                    onClick = { viewModel.importData(req.uri, passphraseInput) }
                ) {
                    Text(stringResource(R.string.privacy_decrypt_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.importData(req.uri, null) /* cancels dialog flow */ }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllData()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.yes_delete_everything), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
