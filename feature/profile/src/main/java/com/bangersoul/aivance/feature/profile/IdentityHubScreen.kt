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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IdentityHubScreen(
    viewModel: IdentityHubViewModel,
    onBack: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToResources: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToProviderManagement: () -> Unit = {},
    onSignedOut: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Identity", "Preferences", "Providers", "Vault", "System")

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
                        onClick = { selectedTab = index },
                        text = { Text(label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "IdentityHubTransition"
                ) { tab ->
                    when (tab) {
                        0 -> IdentityTab(viewModel)
                        1 -> PreferencesTab(viewModel)
                        2 -> ProvidersTab(viewModel, onManageProviders = onNavigateToProviderManagement)
                        3 -> DocumentVaultTab(viewModel)
                        4 -> SystemTab(
                            viewModel,
                            onNavigateToAbout = onNavigateToAbout,
                            onNavigateToResources = onNavigateToResources,
                            onNavigateToAppearance = onNavigateToAppearance,
                            onNavigateToPrivacy = onNavigateToPrivacy
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityTab(viewModel: IdentityHubViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing = uiState.isEditing
    val profile = if (isEditing) uiState.draftProfile else uiState.profile

    if (profile == null) return

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
private fun PreferencesTab(viewModel: IdentityHubViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = uiState.draftProfile ?: return

    var showAddSkillDialog by remember { mutableStateOf(false) }
    var showAddIndustryDialog by remember { mutableStateOf(false) }
    var newSkill by remember { mutableStateOf("") }
    var newIndustry by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Career Preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("These settings influence your recommendations.", style = MaterialTheme.typography.bodySmall)
        }

        item {
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
        }

        item {
            SectionHeader(title = "Target Career Goal")
            OutlinedTextField(
                value = profile.targetRole,
                onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(targetRole = it))) },
                label = { Text("Target Role") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Principal Software Engineer") }
            )
        }

        item {
            SectionHeader(title = "Skills of Interest")
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
        }

        item {
            SectionHeader(title = "Salary Expectation")
            OutlinedTextField(
                value = profile.salaryExpectation,
                onValueChange = { viewModel.onEvent(IdentityHubUiEvent.UpdateDraftProfile(profile.copy(salaryExpectation = it))) },
                label = { Text("Annual Salary") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. $150,000") }
            )
        }

        item {
            SectionHeader(title = "Preferred Industries")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profile.preferredIndustries.forEach { industry ->
                    SuggestionChip(onClick = {}, label = { Text(industry) })
                }
                SuggestionChip(onClick = { showAddIndustryDialog = true }, label = { Text("+ Add") })
            }
        }

        item {
            AivancePrimaryButton(
                text = if (uiState.isSaving) "Saving..." else "Save Preferences",
                onClick = { viewModel.onEvent(IdentityHubUiEvent.SaveDraftProfile) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            )
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

@Composable
private fun ProvidersTab(
    viewModel: IdentityHubViewModel,
    onManageProviders: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Provider Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Manage your AI and Data connectivity.", style = MaterialTheme.typography.bodySmall)
        }

        items(uiState.providers, key = { it.id }) { provider ->
            AivanceWorkspaceCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, AivanceTheme.shapes.small),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when(provider.category) {
                                ProviderCategory.AI -> Icons.Rounded.AutoAwesome
                                ProviderCategory.JOB -> Icons.Rounded.WorkOutline
                                ProviderCategory.ENRICHMENT -> Icons.Rounded.Public
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(provider.name, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(8.dp).background(if (provider.healthStatus == ProviderHealthStatus.HEALTHY) AivanceTheme.colors.success else MaterialTheme.colorScheme.error, CircleShape))
                            Text(provider.healthStatus.name, style = MaterialTheme.typography.labelSmall)
                        }
                        if (provider.isConnected) {
                            Text(provider.maskedApiKey, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    IconButton(onClick = { viewModel.onEvent(IdentityHubUiEvent.TestProvider(provider.id)) }) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(20.dp))
                    }
                    Switch(checked = provider.isEnabled, onCheckedChange = { viewModel.onEvent(IdentityHubUiEvent.ToggleProvider(provider.id, it)) })
                }
            }
        }

        item {
            AivanceSecondaryButton(
                text = "Manage Providers — API Keys & Models",
                onClick = onManageProviders,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Tune
            )
        }
    }
}

@Composable
private fun DocumentVaultTab(viewModel: IdentityHubViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Document Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Securely manage your career assets.", style = MaterialTheme.typography.bodySmall)
        }

        if (uiState.documents.isEmpty()) {
            item {
                AivanceEmptyState(
                    title = "No documents found",
                    description = "Upload your resumes or certificates to keep them organized.",
                    icon = Icons.Rounded.Description
                )
            }
        } else {
            items(uiState.documents, key = { it.id }) { resume ->
                AivanceWorkspaceCard {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(resume.name, fontWeight = FontWeight.Bold)
                            Text("Resume · ${resume.fileName}", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Rounded.MoreVert, null)
                        }
                    }
                }
            }
        }

        item {
            AivanceSecondaryButton(
                text = "Upload Document",
                onClick = { /* Open Picker */ },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Upload
            )
        }
    }
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
