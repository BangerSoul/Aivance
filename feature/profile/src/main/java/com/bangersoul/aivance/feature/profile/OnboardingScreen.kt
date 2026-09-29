package com.bangersoul.aivance.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.feature.profile.OnboardingUiEvent
import com.bangersoul.aivance.feature.profile.OnboardingUiState
import com.bangersoul.aivance.feature.profile.OnboardingViewModel
import com.bangersoul.aivance.core.designsystem.components.AivancePrimaryButton
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import com.bangersoul.aivance.sdk.core.ConfigField
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.sdk.core.ProviderMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = uiState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        // Key by the step type, not the whole state object: typing into a config
        // field updates `config` on the same step, which must recompose the
        // existing text field in place instead of tearing down the composition
        // and restarting the fade every keystroke. The tear-down dropped the
        // IME session on real devices, making a pasted API key vanish.
        contentKey = { it::class },
        label = "OnboardingTransition"
    ) { state ->
        when (state) {
            is OnboardingUiState.ChooseAiProvider -> ProviderSelectionStep(
                title = stringResource(R.string.choose_ai_provider),
                description = stringResource(R.string.ai_provider_desc),
                providers = state.providers,
                onSelect = { viewModel.onEvent(OnboardingUiEvent.SelectAiProvider(it)) },                onSkipAll = { viewModel.onEvent(OnboardingUiEvent.ContinueWithoutProviders) }
            )

            is OnboardingUiState.ConfigureAiProvider -> ProviderConfigStep(
                title = "Configure ${state.provider.name}",
                provider = state.provider,
                config = state.config,
                isValidating = state.isValidating,
                error = state.error,
                isOnDevice = state.isOnDevice,
                modelReady = state.modelReady,
                isDownloading = state.isDownloading,
                downloadProgress = state.downloadProgress,
                downloadMessage = state.downloadMessage,
                onUpdate = { k, v -> viewModel.onEvent(OnboardingUiEvent.UpdateAiConfig(k, v)) },
                onValidate = { viewModel.onEvent(OnboardingUiEvent.ValidateAiProvider) },
                onBack = { viewModel.onEvent(OnboardingUiEvent.Back) },
                onDownloadModel = { viewModel.onEvent(OnboardingUiEvent.DownloadModel) },
                onDismissDownloadMessage = { viewModel.onEvent(OnboardingUiEvent.DismissDownloadMessage) }
            )

            is OnboardingUiState.ChooseJobProvider -> ProviderSelectionStep(
                title = stringResource(R.string.choose_job_provider),
                description = stringResource(R.string.job_provider_desc),
                providers = state.providers,
                onSelect = { viewModel.onEvent(OnboardingUiEvent.SelectJobProvider(it)) },                onSkipAll = { viewModel.onEvent(OnboardingUiEvent.ContinueWithoutProviders) }
            )

            is OnboardingUiState.ConfigureJobProvider -> ProviderConfigStep(
                title = "Configure ${state.provider.name}",
                provider = state.provider,
                config = state.config,
                isValidating = state.isValidating,
                error = state.error,
                onUpdate = { k, v -> viewModel.onEvent(OnboardingUiEvent.UpdateJobConfig(k, v)) },
                onValidate = { viewModel.onEvent(OnboardingUiEvent.ValidateJobProvider) },
                onBack = { viewModel.onEvent(OnboardingUiEvent.Back) }
            )

            is OnboardingUiState.ChooseEnrichmentProvider -> ProviderSelectionStep(
                title = stringResource(R.string.enrichment_optional),
                description = stringResource(R.string.enrichment_desc),
                providers = state.providers,
                onSelect = { viewModel.onEvent(OnboardingUiEvent.SelectEnrichmentProvider(it)) },
                onSkip = { viewModel.onEvent(OnboardingUiEvent.SkipEnrichment) }
            )

            is OnboardingUiState.ConfigureEnrichmentProvider -> ProviderConfigStep(
                title = "Configure ${state.provider.name}",
                provider = state.provider,
                config = state.config,
                isValidating = state.isValidating,
                error = state.error,
                onUpdate = { k, v -> viewModel.onEvent(OnboardingUiEvent.UpdateEnrichmentConfig(k, v)) },
                onValidate = { viewModel.onEvent(OnboardingUiEvent.ValidateEnrichmentProvider) },
                onBack = { viewModel.onEvent(OnboardingUiEvent.Back) }
            )

            is OnboardingUiState.Summary -> OnboardingSummaryStep(
                aiProvider = state.aiProvider,
                jobProvider = state.jobProvider,
                enrichmentProvider = state.enrichmentProvider,
                onFinish = { viewModel.onEvent(OnboardingUiEvent.Finish) }
            )

            OnboardingUiState.Complete -> {
                LaunchedEffect(Unit) { onComplete() }
                Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ProviderSelectionStep(
    title: String,
    description: String,
    providers: List<ProviderMetadata>,
    onSelect: (String) -> Unit,
    onSkip: (() -> Unit)? = null,
    onSkipAll: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).imePadding()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(providers, key = { it.id }) { provider ->
                Card(
                    onClick = { onSelect(provider.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Settings, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider.name, fontWeight = FontWeight.Bold)
                            Text(provider.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.NavigateNext, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.skip_for_now))
            }
        } else {
            TextButton(onClick = onSkipAll, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.skip_all))
            }
            Text(
                stringResource(R.string.configure_later),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProviderConfigStep(
    title: String,
    provider: ProviderMetadata,
    config: Map<String, String>,
    isValidating: Boolean,
    error: String?,
    isOnDevice: Boolean = false,
    modelReady: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float? = null,
    downloadMessage: String? = null,
    onUpdate: (String, String) -> Unit,
    onValidate: () -> Unit,
    onBack: () -> Unit,
    onDownloadModel: () -> Unit = {},
    onDismissDownloadMessage: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).imePadding()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        if (isOnDevice) {
            // On-device providers (e.g. Gemma) are configured by downloading the
            // model file — never by typing a URL. Show the download status
            // directly instead of the credential form.
            OnDeviceDownloadPanel(
                modelReady = modelReady,
                isDownloading = isDownloading,
                progress = downloadProgress,
                message = downloadMessage,
                onDownload = onDownloadModel,
                onDismissMessage = onDismissDownloadMessage
            )
        } else {
            provider.configFields.forEach { field ->
                DynamicField(
                    field = field,
                    value = config[field.key] ?: "",
                    onValueChange = { onUpdate(field.key, it) }
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onValidate,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            // On-device providers have no credentials: Continue is just the model
            // readiness gate — disabled until the download lands.
            enabled = !isValidating && (!isOnDevice || modelReady),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isValidating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(
                    if (isOnDevice) stringResource(R.string.continue_label) else stringResource(R.string.validate_continue),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}

/**
 * The on-device model section: green "Downloaded" when the file is present,
 * a live progress bar while downloading, and a single Download-model button
 * otherwise — no URL input, no credential form.
 */
@Composable
private fun OnDeviceDownloadPanel(
    modelReady: Boolean,
    isDownloading: Boolean,
    progress: Float?,
    message: String?,
    onDownload: () -> Unit,
    onDismissMessage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (modelReady) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = AivanceTheme.colors.successContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = AivanceTheme.colors.onSuccessContainer
                    )
                    Column {
                        Text(
                            stringResource(R.string.model_downloaded_status),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AivanceTheme.colors.onSuccessContainer
                        )
                        Text(
                            stringResource(R.string.model_offline_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = AivanceTheme.colors.onSuccessContainer
                        )
                    }
                }
            }
        } else if (isDownloading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.model_downloading_percent,
                            ((progress ?: 0f) * 100).toInt()
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.model_download_background_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.model_download_required),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.model_download_required_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AivancePrimaryButton(
                        text = stringResource(R.string.download_model),
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Download
                    )
                }
            }
        }

        message?.let { notice ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissMessage) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicField(
    field: ConfigField,
    value: String,
    onValueChange: (String) -> Unit
) {
    val isPassword = field.fieldType == FieldType.PASSWORD
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(field.label) },
        placeholder = { field.hint?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            // Password keyboard type + no autocorrect/capitalization so API keys
            // (which contain mixed case, digits and underscores) are typed exactly
            // as entered instead of being silently rewritten by the IME.
            // autoCorrectEnabled is the non-deprecated constructor parameter for
            // Compose foundation 1.7.x (BOM 2024.09.00); autoCorrect is deprecated.
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None
        ),
        singleLine = true
    )
}

@Composable
private fun OnboardingSummaryStep(
    aiProvider: String,
    jobProvider: String,
    enrichmentProvider: String?,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(80.dp), tint = Color(0xFF4CAF50))
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.all_set), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        SummaryItem(label = stringResource(R.string.ai_engine), value = aiProvider)
        Spacer(Modifier.height(12.dp))
        SummaryItem(label = stringResource(R.string.job_source), value = jobProvider)
        Spacer(Modifier.height(12.dp))
        SummaryItem(label = stringResource(R.string.enrichment), value = enrichmentProvider ?: stringResource(R.string.not_configured))

        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.go_to_dashboard), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(value, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        }
    }
}
