package com.bangersoul.aivance.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import java.util.Locale
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import com.bangersoul.aivance.sdk.core.ConfigField
import com.bangersoul.aivance.sdk.core.FieldType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IdentityHubScreen(
    viewModel: IdentityHubViewModel,
    onBack: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToResources: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    /**
     * Selected sub-tab, owned by the caller (B5). A local `remember` here was
     * wiped every time the user left the hub for a System spoke (Appearance,
     * Privacy, …) and came back — the spoke push/replace re-creates this
     * composable, so the hub snaps back to the Identity tab.
     */
    selectedTab: Int = 0,
    onSignedOut: () -> Unit = {},
    onTabChange: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Three tabs: Preferences is a section of Identity (AUDIT 20), the provider
    // surface is a single hub (AUDIT 22), and the document vault folded into
    // Studio ▸ Resumes (AUDIT 23) — which already owns the same resume list from
    // ResumeRepository, so the hub no longer carries a second document surface.
    val tabs = listOf("Identity", "Providers", "System")

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                IdentityHubUiEffect.SignOutCompleted -> onSignedOut()
            }
        }
    }

    AivanceWorkspaceScaffold(
        title = "Identity Hub",
        subtitle = "Control your career operating system",
        onBack = onBack,
        isLoading = uiState.isLoading,
        error = uiState.error,
        onRetry = { viewModel.refresh() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onTabChange(index) },
                        text = { Text(label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "IdentityHubTransition"
                ) { tab ->                    when (tab) {
                        1 -> ProvidersTab()
                        2 -> SystemTab(
                            viewModel,
                            onNavigateToAbout = onNavigateToAbout,
                            onNavigateToResources = onNavigateToResources,
                            onNavigateToAppearance = onNavigateToAppearance,
                            onNavigateToPrivacy = onNavigateToPrivacy
                        )
                        // Identity is also the fallback: a tab index saved before
                        // the Preferences/Vault merges would otherwise land nowhere.
                        else -> IdentityTab(viewModel)
                    }
                }
            }
        }
    }
}

/**
 * One profile editor (AUDIT 20). "Identity" and "Preferences" were the same
 * `UserProfile` — both wrote `draftProfile` through `UpdateDraftProfile`, and
 * both committed the whole record through `SaveDraftProfile`, so the hub asked
 * for one profile twice and offered two Save buttons. The career preferences are
 * now a section of Identity under the same single Edit → Save flow, which also
 * means a preference change is no longer stranded when the user leaves without
 * pressing the second Save.
 */
@Composable
private fun IdentityTab(viewModel: IdentityHubViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing = uiState.isEditing
    val profile = if (isEditing) uiState.draftProfile else uiState.profile

    if (profile == null) return

    var showAddSkillDialog by remember { mutableStateOf(false) }
    var showAddIndustryDialog by remember { mutableStateOf(false) }
    var newSkill by remember { mutableStateOf("") }
    var newIndustry by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                IdentityHeader(profile)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(title = "Personal Information")
                    TextButton(onClick = { viewModel.onEvent(IdentityHubUiEvent.ToggleEdit) }) {
                        Text(if (isEditing) "Cancel" else "Edit")
                    }
                }

                if (isEditing) {
                    OutlinedTextField(
                        value = profile.fullName,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(fullName = it))) },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = profile.phone,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(phone = it))) },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    IdentityField(label = "Full Name", value = profile.fullName)
                    IdentityField(label = "Email", value = profile.email, isReadOnly = true)
                    IdentityField(label = "Phone", value = profile.phone)
                }
            }

            item {
                SectionHeader(title = "Professional Experience")
                if (isEditing) {
                    OutlinedTextField(
                        value = profile.currentRole,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(currentRole = it))) },
                        label = { Text("Current Role") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = profile.company,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(company = it))) },
                        label = { Text("Company") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    IdentityField(label = "Current Role", value = profile.currentRole)
                    IdentityField(label = "Company", value = profile.company)
                    IdentityField(label = "Experience", value = "${profile.experienceYears} years")
                }
            }

            item {
                SectionHeader(title = "Career Preferences")
                Text("These settings influence your recommendations.", style = MaterialTheme.typography.bodySmall)

                if (isEditing) {
                    Spacer(Modifier.height(8.dp))
                    AivanceWorkspaceCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PreferenceToggle(
                                label = "Remote Work",
                                checked = profile.workPreference == "REMOTE",
                                onCheckedChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(workPreference = if (it) "REMOTE" else "ONSITE"))) }
                            )
                            PreferenceToggle(
                                label = "Visa Sponsorship Required",
                                checked = profile.visaRequired,
                                onCheckedChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(visaRequired = it))) }
                            )
                        }
                    }
                } else {
                    IdentityField(
                        label = "Remote Work",
                        value = if (profile.workPreference == "REMOTE") "Yes" else "No"
                    )
                    IdentityField(
                        label = "Visa Sponsorship",
                        value = if (profile.visaRequired) "Required" else "Not required"
                    )
                }
            }

            item {
                SectionHeader(title = "Target Career Goal")
                if (isEditing) {
                    OutlinedTextField(
                        value = profile.targetRole,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(targetRole = it))) },
                        label = { Text("Target Role") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Principal Software Engineer") }
                    )
                } else {
                    IdentityField(label = "Target Role", value = profile.targetRole)
                }
            }

            item {
                SectionHeader(title = "Skills of Interest")
                if (isEditing) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.skills.forEach { skill ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(skills = profile.skills.filterNot { it == skill }))) },
                                label = { Text(skill) },
                                trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }
                            )
                        }
                        SuggestionChip(onClick = { showAddSkillDialog = true }, label = { Text("+ Add Skill") })
                    }
                } else {
                    IdentityField(label = "Skills", value = profile.skills.joinToString(", "))
                }
            }

            item {
                SectionHeader(title = "Salary Expectation")
                if (isEditing) {
                    OutlinedTextField(
                        value = profile.salaryExpectation,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(salaryExpectation = it))) },
                        label = { Text("Annual Salary") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. $150,000") }
                    )
                } else {
                    IdentityField(label = "Annual Salary", value = profile.salaryExpectation)
                }
            }

            item {
                SectionHeader(title = "Preferred Industries")
                if (isEditing) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.preferredIndustries.forEach { industry ->
                            SuggestionChip(onClick = {}, label = { Text(industry) })
                        }
                        SuggestionChip(onClick = { showAddIndustryDialog = true }, label = { Text("+ Add") })
                    }
                } else {
                    IdentityField(label = "Industries", value = profile.preferredIndustries.joinToString(", "))
                }
            }

            if (isEditing) {
                item {
                    AivancePrimaryButton(
                        text = if (uiState.isSaving) "Saving..." else "Save Changes",
                        onClick = { viewModel.onEvent(IdentityHubUiEvent.SaveDraftProfile) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving
                    )
                }
            }
        }

        // Add Skill / Add Industry dialogs — wires the previously dead chips.
        if (showAddSkillDialog) {
            AlertDialog(
                onDismissRequest = { showAddSkillDialog = false },
                title = { Text("Add Skill") },
                text = {
                    OutlinedTextField(
                        value = newSkill,
                        onValueChange = { newSkill = it },
                        label = { Text("Skill") },
                        placeholder = { Text("e.g. Jetpack Compose") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val skill = newSkill.trim()
                            if (skill.isNotBlank()) {
                                viewModel.onEvent(
                                    IdentityHubUiEvent.UpdateDraftProfile(
                                        profile.copy(skills = (profile.skills + skill).distinct())
                                    )
                                )
                            }
                            newSkill = ""
                            showAddSkillDialog = false
                        }
                    ) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSkillDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showAddIndustryDialog) {
            AlertDialog(
                onDismissRequest = { showAddIndustryDialog = false },
                title = { Text("Add Preferred Industry") },
                text = {
                    OutlinedTextField(
                        value = newIndustry,
                        onValueChange = { newIndustry = it },
                        label = { Text("Industry") },
                        placeholder = { Text("e.g. Fintech") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val industry = newIndustry.trim()
                            if (industry.isNotBlank()) {
                                viewModel.onEvent(
                                    IdentityHubUiEvent.UpdateDraftProfile(
                                        profile.copy(preferredIndustries = (profile.preferredIndustries + industry).distinct())
                                    )
                                )
                            }
                            newIndustry = ""
                            showAddIndustryDialog = false
                        }
                    ) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddIndustryDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun IdentityHeader(profile: UserProfile) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = AivanceTheme.colors.accent.copy(alpha = 0.1f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = profile.fullName.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = AivanceTheme.colors.accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column {
            Text(profile.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(profile.targetRole, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IdentityField(label: String, value: String, isReadOnly: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            text = value.ifBlank { "Not provided" },
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
        )
        if (!isReadOnly) {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun PreferenceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AivanceWorkspaceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AivanceTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvidersTab(
    viewModel: ProviderManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProviderManagementUiEffect.ShowSnackbar ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is ProviderManagementUiEffect.ConnectionTestResult ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    when (val state = uiState) {
        is ProviderManagementUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading providers…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is ProviderManagementUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                AivancePrimaryButton(
                    text = "Retry",
                    onClick = { viewModel.onEvent(ProviderManagementUiEvent.Refresh) }
                )
            }
        }
        is ProviderManagementUiState.Success -> {
            ProvidersList(state = state, onEvent = viewModel::onEvent)
            state.modelDownloadDialog?.let { dialog ->
                ModelDownloadConfirmationDialog(
                    dialog = dialog,
                    onConfirm = { useCompact ->
                        viewModel.onEvent(
                            ProviderManagementUiEvent.ConfirmModelDownload(dialog.providerId, useCompact)
                        )
                    },
                    onDismiss = { viewModel.onEvent(ProviderManagementUiEvent.DismissModelDownloadDialog) }
                )
            }
        }
    }
}

/**
 * The single Providers surface (AUDIT 22): one metadata-driven list grouped
 * into "AI" then "Job Boards". Enrichment providers are intentionally excluded
 * from the default hub list. Each card carries the full config UI — credential
 * form, on-device model download/delete, model picker, Test and Save — so the
 * hub tab is the only provider surface and the standalone Provider Management
 * route is gone.
 */
@Composable
private fun ProvidersList(
    state: ProviderManagementUiState.Success,
    onEvent: (ProviderManagementUiEvent) -> Unit
) {
    val aiProviders = state.providers.filter { it.category == ProviderCategory.AI }
    val jobProviders = state.providers.filter { it.category == ProviderCategory.JOB }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Providers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Manage your AI and Job connectivity.", style = MaterialTheme.typography.bodySmall)
        }

        if (aiProviders.isNotEmpty()) {
            item { ProviderSectionLabel("AI") }
            items(aiProviders, key = { it.id }) { provider ->
                ProviderCard(provider, state, onEvent)
            }
        }

        if (jobProviders.isNotEmpty()) {
            item { ProviderSectionLabel("Job Boards") }
            items(jobProviders, key = { it.id }) { provider ->
                ProviderCard(provider, state, onEvent)
            }
        }
    }
}

@Composable
private fun ProviderSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ProviderCard(
    provider: ProviderInfo,
    state: ProviderManagementUiState.Success,
    onEvent: (ProviderManagementUiEvent) -> Unit
) {
    val credentialDrafts = state.credentialDrafts[provider.id].orEmpty()
    var modelMenuOpen by remember { mutableStateOf(false) }

    AivanceWorkspaceCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, AivanceTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (provider.category) {
                            ProviderCategory.AI -> Icons.Rounded.AutoAwesome
                            ProviderCategory.JOB -> Icons.Rounded.WorkOutline
                            else -> Icons.Rounded.Public
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (provider.description.isNotBlank()) {
                        Text(
                            provider.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    if (provider.apiKeyConfigured && provider.maskedApiKey.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = provider.maskedApiKey,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                ProviderHealthChip(provider.healthStatus)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (provider.isEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (provider.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = { onEvent(ProviderManagementUiEvent.ToggleProvider(provider.id, it)) }
                )
            }

            if (provider.isOnDevice) {
                val isDownloading = state.downloadingProviderId == provider.id
                if (provider.modelDownloaded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = AivanceTheme.shapes.small,
                            color = AivanceTheme.colors.successContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = AivanceTheme.colors.onSuccessContainer
                                )
                                Text(
                                    "Downloaded",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AivanceTheme.colors.onSuccessContainer
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        AivanceSecondaryButton(
                            text = "Delete model",
                            onClick = { onEvent(ProviderManagementUiEvent.DeleteModel(provider.id)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Downloading model… ${((state.modelDownloadProgress ?: 0f) * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { state.modelDownloadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Model not downloaded — download once to use this provider fully offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AivancePrimaryButton(
                            text = "Download model",
                            onClick = { onEvent(ProviderManagementUiEvent.DownloadModel(provider.id)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (provider.configFields.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    provider.configFields.forEach { field ->
                        ProviderCredentialField(
                            field = field,
                            value = credentialDrafts[field.key].orEmpty(),
                            onValueChange = { onEvent(ProviderManagementUiEvent.SetCredential(provider.id, field.key, it)) }
                        )
                    }
                }
            }

            if (provider.availableModels.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Model", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.3f))
                    OutlinedButton(
                        onClick = { modelMenuOpen = true },
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text(
                            provider.selectedModel.ifBlank { "Select…" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false }
                    ) {
                        provider.availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    modelMenuOpen = false
                                    onEvent(ProviderManagementUiEvent.SelectModel(provider.id, model))
                                }
                            )
                        }
                    }
                }
            }

            // Keyless on-device providers need no credentials: Save/Test are
            // meaningless, so download/delete above are their only actions.
            if (!provider.isOnDevice) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AivanceSecondaryButton(
                        text = "Save",
                        onClick = { onEvent(ProviderManagementUiEvent.SaveProvider(provider.id)) },
                        modifier = Modifier.weight(1f)
                    )
                    AivancePrimaryButton(
                        text = if (state.testingProviderId == provider.id) "Testing…" else "Test",
                        onClick = { onEvent(ProviderManagementUiEvent.TestConnection(provider.id)) },
                        modifier = Modifier.weight(1f),
                        enabled = state.testingProviderId != provider.id
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCredentialField(
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
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None
        ),
        singleLine = true
    )
}

@Composable
private fun ProviderHealthChip(status: ProviderHealthStatus) {
    val (tone, label) = when (status) {
        ProviderHealthStatus.HEALTHY -> BannerTone.SUCCESS to "Healthy"
        ProviderHealthStatus.DEGRADED -> BannerTone.WARNING to "Degraded"
        ProviderHealthStatus.UNHEALTHY -> BannerTone.ERROR to "Unhealthy"
        ProviderHealthStatus.UNKNOWN -> BannerTone.INFO to "Unknown"
    }
    StatusChip(text = label, tone = tone)
}

/** Formats a byte count for display, e.g. `3.0 GB` or `271 MB`. */
private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    val mib = bytes / (1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(Locale.US, "%.1f GB", gib)
    } else {
        String.format(Locale.US, "%.0f MB", mib)
    }
}

@Composable
private fun ModelDownloadConfirmationDialog(
    dialog: ModelDownloadDialog,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download on-device model", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Model size: ${formatBytes(dialog.modelSizeBytes)} (${dialog.modelSizeBytes} bytes)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Free storage: ${formatBytes(dialog.freeStorageBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dialog.ramWarning) {
                    Text(
                        "This device has less than 4 GB of RAM. The full model may run slowly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (dialog.storageBlocked) {
                    Text(
                        "Not enough free storage for the full model. The smaller model fits — use it instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (dialog.offersCompact && dialog.compactName != null) {
                    Surface(
                        shape = AivanceTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Download smaller model (${formatBytes(dialog.compactSizeBytes)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Uses far less storage and RAM.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!dialog.storageBlocked) {
                    TextButton(onClick = { onConfirm(false) }) {
                        Text("Download")
                    }
                }
                if (dialog.offersCompact) {
                    TextButton(onClick = { onConfirm(true) }) {
                        Text("Download smaller model (${formatBytes(dialog.compactSizeBytes)})")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
private fun SystemTab(
    viewModel: IdentityHubViewModel,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToResources: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("System Controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            SectionHeader(title = "Appearance")
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNavigateToAppearance, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Palette, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Appearance & Theme")
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Security & Privacy")
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNavigateToPrivacy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.PrivacyTip, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Privacy & Security")
                    }
                    TextButton(onClick = onNavigateToResources, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.MenuBook, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Remote Work Resources")
                    }
                }
            }
        }

        item {
            SectionHeader(title = "About")
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNavigateToAbout, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Info, null)
                        Spacer(Modifier.width(8.dp))
                        Text("About AiVance")
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Data Management")
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            // Wires the previously dead Export button: shares the
                            // profile as portable text so the user keeps their data.
                            val profile = uiState.profile ?: return@TextButton
                            val payload = buildString {
                                appendLine("AiVance Career Data Export")
                                appendLine("Name: ").append(profile.fullName)
                                appendLine("Target Role: ").append(profile.targetRole)
                                appendLine("Skills: ").append(profile.skills.joinToString(", "))
                                appendLine("Preferred Industries: ").append(profile.preferredIndustries.joinToString(", "))
                                appendLine("Salary Expectation: ").append(profile.salaryExpectation)
                                appendLine("Work Preference: ").append(profile.workPreference)
                            }
                            val sendIntent = android.content.Intent(
                                android.content.Intent.ACTION_SEND
                            ).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, payload)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "AiVance Career Data")
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(sendIntent, "Export Career Data")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CloudDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Career Data")
                    }
                    TextButton(onClick = { viewModel.onEvent(IdentityHubUiEvent.ResetAll) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Rounded.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reset All Settings")
                    }
                    TextButton(onClick = { viewModel.onEvent(IdentityHubUiEvent.SignOut) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("AiVance v2.0.0 (BETA)", style = MaterialTheme.typography.labelSmall)
                Text("Your Career Operating System", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
