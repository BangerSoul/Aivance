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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
    val tabs = listOf(stringResource(R.string.profile_tab_identity), stringResource(R.string.providers_title), stringResource(R.string.profile_tab_system))

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                IdentityHubUiEffect.SignOutCompleted -> onSignedOut()
            }
        }
    }

    AivanceWorkspaceScaffold(
        title = stringResource(R.string.profile_hub_title),
        subtitle = stringResource(R.string.profile_hub_subtitle),
        backContentDescription = stringResource(R.string.back),
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
                    SectionHeader(title = stringResource(R.string.profile_section_personal))
                    TextButton(onClick = { viewModel.onEvent(IdentityHubUiEvent.ToggleEdit) }) {
                        Text(if (isEditing) stringResource(R.string.cancel) else stringResource(R.string.edit))
                    }
                }

                if (isEditing) {
                    OutlinedTextField(
                        value = profile.fullName,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(fullName = it))) },
                        label = { Text(stringResource(R.string.profile_full_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = profile.phone,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(phone = it))) },
                        label = { Text(stringResource(R.string.profile_phone)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    IdentityField(label = stringResource(R.string.profile_full_name), value = profile.fullName)
                    IdentityField(label = stringResource(R.string.profile_email), value = profile.email, isReadOnly = true)
                    IdentityField(label = stringResource(R.string.profile_phone), value = profile.phone)
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.profile_section_experience))
                if (isEditing) {
                    OutlinedTextField(
                        value = profile.currentRole,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(currentRole = it))) },
                        label = { Text(stringResource(R.string.profile_current_role)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = profile.company,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(company = it))) },
                        label = { Text(stringResource(R.string.company)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    IdentityField(label = stringResource(R.string.profile_current_role), value = profile.currentRole)
                    IdentityField(label = stringResource(R.string.company), value = profile.company)
                    IdentityField(label = stringResource(R.string.profile_experience), value = pluralStringResource(R.plurals.profile_experience_years, profile.experienceYears, profile.experienceYears))
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.profile_section_preferences))
                Text(stringResource(R.string.profile_preferences_hint), style = MaterialTheme.typography.bodySmall)

                if (isEditing) {
                    Spacer(Modifier.height(8.dp))
                    AivanceWorkspaceCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PreferenceToggle(
                                label = stringResource(R.string.profile_remote_work),
                                checked = profile.workPreference == "REMOTE",
                                onCheckedChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(workPreference = if (it) "REMOTE" else "ONSITE"))) }
                            )
                            PreferenceToggle(
                                label = stringResource(R.string.profile_visa_required),
                                checked = profile.visaRequired,
                                onCheckedChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(visaRequired = it))) }
                            )
                        }
                    }
                } else {
                    IdentityField(
                        label = stringResource(R.string.profile_remote_work),
                        value = if (profile.workPreference == "REMOTE") stringResource(R.string.profile_yes) else stringResource(R.string.profile_no)
                    )
                    IdentityField(
                        label = stringResource(R.string.profile_visa),
                        value = if (profile.visaRequired) stringResource(R.string.profile_required) else stringResource(R.string.profile_not_required)
                    )
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.profile_section_goal))
                if (isEditing) {
                    OutlinedTextField(
                        value = profile.targetRole,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(targetRole = it))) },
                        label = { Text(stringResource(R.string.target_role)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.profile_target_role_hint)) }
                    )
                } else {
                    IdentityField(label = stringResource(R.string.target_role), value = profile.targetRole)
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.profile_section_skills))
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
                        SuggestionChip(onClick = { showAddSkillDialog = true }, label = { Text(stringResource(R.string.profile_add_skill)) })
                    }
                } else {
                    IdentityField(label = stringResource(R.string.profile_skills_label), value = profile.skills.joinToString(", "))
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.profile_section_salary))
                if (isEditing) {
                    OutlinedTextField(
                        value = profile.salaryExpectation,
                        onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(salaryExpectation = it))) },
                        label = { Text(stringResource(R.string.profile_salary_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.profile_salary_hint)) }
                    )
                } else {
                    IdentityField(label = stringResource(R.string.profile_salary_label), value = profile.salaryExpectation)
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.profile_section_industries))
                if (isEditing) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.preferredIndustries.forEach { industry ->
                            SuggestionChip(onClick = {}, label = { Text(industry) })
                        }
                        SuggestionChip(onClick = { showAddIndustryDialog = true }, label = { Text(stringResource(R.string.profile_add_industry_chip)) })
                    }
                } else {
                    IdentityField(label = stringResource(R.string.profile_industries_label), value = profile.preferredIndustries.joinToString(", "))
                }
            }

            if (isEditing) {
                item {
                    AivancePrimaryButton(
                        text = if (uiState.isSaving) stringResource(R.string.profile_saving) else stringResource(R.string.profile_save_changes),
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
                title = { Text(stringResource(R.string.profile_add_skill_title)) },
                text = {
                    OutlinedTextField(
                        value = newSkill,
                        onValueChange = { newSkill = it },
                        label = { Text(stringResource(R.string.profile_skill_label)) },
                        placeholder = { Text(stringResource(R.string.profile_skill_hint)) },
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
                    ) { Text(stringResource(R.string.profile_add)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSkillDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showAddIndustryDialog) {
            AlertDialog(
                onDismissRequest = { showAddIndustryDialog = false },
                title = { Text(stringResource(R.string.profile_add_industry_title)) },
                text = {
                    OutlinedTextField(
                        value = newIndustry,
                        onValueChange = { newIndustry = it },
                        label = { Text(stringResource(R.string.profile_industry_label)) },
                        placeholder = { Text(stringResource(R.string.profile_industry_hint)) },
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
                    ) { Text(stringResource(R.string.profile_add)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddIndustryDialog = false }) { Text(stringResource(R.string.cancel)) }
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
            text = value.ifBlank { stringResource(R.string.profile_not_provided) },
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
                Text(stringResource(R.string.providers_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    text = stringResource(R.string.providers_retry),
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
            Text(stringResource(R.string.providers_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.providers_subtitle), style = MaterialTheme.typography.bodySmall)
        }

        if (aiProviders.isNotEmpty()) {
            item { ProviderSectionLabel(stringResource(R.string.providers_section_ai)) }
            items(aiProviders, key = { it.id }) { provider ->
                ProviderCard(provider, state, onEvent)
            }
        }

        if (jobProviders.isNotEmpty()) {
            item { ProviderSectionLabel(stringResource(R.string.providers_section_job_boards)) }
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
                    if (provider.isEnabled) stringResource(R.string.providers_enabled) else stringResource(R.string.providers_disabled),
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
                                    stringResource(R.string.model_downloaded_status),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AivanceTheme.colors.onSuccessContainer
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        AivanceSecondaryButton(
                            text = stringResource(R.string.providers_delete_model),
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
                            stringResource(R.string.model_downloading_percent, ((state.modelDownloadProgress ?: 0f) * 100).toInt()),
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
                            stringResource(R.string.providers_not_downloaded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AivancePrimaryButton(
                            text = stringResource(R.string.download_model),
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
                    Text(stringResource(R.string.providers_model_label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.3f))
                    OutlinedButton(
                        onClick = { modelMenuOpen = true },
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text(
                            provider.selectedModel.ifBlank { stringResource(R.string.providers_select_model) },
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
                        text = stringResource(R.string.providers_save),
                        onClick = { onEvent(ProviderManagementUiEvent.SaveProvider(provider.id)) },
                        modifier = Modifier.weight(1f)
                    )
                    AivancePrimaryButton(
                        text = if (state.testingProviderId == provider.id) stringResource(R.string.providers_testing) else stringResource(R.string.providers_test),
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
        ProviderHealthStatus.HEALTHY -> BannerTone.SUCCESS to stringResource(R.string.providers_health_healthy)
        ProviderHealthStatus.DEGRADED -> BannerTone.WARNING to stringResource(R.string.providers_health_degraded)
        ProviderHealthStatus.UNHEALTHY -> BannerTone.ERROR to stringResource(R.string.providers_health_unhealthy)
        ProviderHealthStatus.UNKNOWN -> BannerTone.INFO to stringResource(R.string.providers_health_unknown)
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
        title = { Text(stringResource(R.string.providers_download_dialog_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.providers_model_size, formatBytes(dialog.modelSizeBytes), dialog.modelSizeBytes),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.providers_free_storage, formatBytes(dialog.freeStorageBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dialog.ramWarning) {
                    Text(
                        stringResource(R.string.providers_ram_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (dialog.storageBlocked) {
                    Text(
                        stringResource(R.string.providers_storage_blocked),
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
                                text = stringResource(R.string.providers_download_compact, formatBytes(dialog.compactSizeBytes)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.providers_compact_note),
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
                        Text(stringResource(R.string.providers_download))
                    }
                }
                if (dialog.offersCompact) {
                    TextButton(onClick = { onConfirm(true) }) {
                        Text(stringResource(R.string.providers_download_compact, formatBytes(dialog.compactSizeBytes)))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(stringResource(R.string.system_controls_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            SectionHeader(title = stringResource(R.string.appearance_title))
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNavigateToAppearance, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Palette, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.system_appearance_item))
                    }
                }
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.system_security_section))
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNavigateToPrivacy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.PrivacyTip, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.privacy_title))
                    }
                    TextButton(onClick = onNavigateToResources, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.MenuBook, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.resources_title))
                    }
                }
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.system_about_section))
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNavigateToAbout, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Info, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.about_title))
                    }
                }
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.system_data_section))
            AivanceWorkspaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Encrypted backup/restore lives in Privacy Center (AUDIT 24) —
                    // the single, passphrase-protected backup surface. The old
                    // plaintext career-data export chooser duplicated it with a
                    // weaker format, so it is gone.
                    TextButton(onClick = { viewModel.onEvent(IdentityHubUiEvent.ResetAll) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Rounded.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.system_reset))
                    }
                    TextButton(onClick = { viewModel.onEvent(IdentityHubUiEvent.SignOut) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.system_sign_out))
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.system_version_banner, "2.0.0"), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.system_tagline), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
