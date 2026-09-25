package com.bangersoul.aivance.feature.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.common.enums.EmploymentType
import com.bangersoul.aivance.core.common.enums.ExperienceLevel
import com.bangersoul.aivance.core.common.enums.RemotePolicy
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

@Composable
fun JobsScreen(
    viewModel: JobsViewModel,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToSavedJobs: () -> Unit = {},
    initialQuery: String? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Seed the search once when arriving from a deep link (e.g. a dashboard
    // skill-gap chip). The ViewModel guards against re-running on the same
    // seed after rotation/process death, so a user's later edits stick.
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onEvent(JobsUiEvent.SeedSearch(initialQuery))
        }
    }
    // rememberSaveable: the typed query survives rotation/process death (the
    // ViewModel also persists it in SavedStateHandle, so it stays until app
    // data is cleared).
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Best-match sort: opt-in, ranked by merged AI/rule-based fit scores (R-04).
    var sortByFit by rememberSaveable { mutableStateOf(false) }

    AivanceWorkspaceScaffold(
        title = stringResource(R.string.job_discovery_title),
        subtitle = stringResource(R.string.job_discovery_subtitle),
        isLoading = uiState is JobsUiState.Loading,
        error = (uiState as? JobsUiState.Error)?.message,
        onRetry = { viewModel.onEvent(JobsUiEvent.Refresh) }
    ) {
        // Single scrollable surface: hero, search, filters and sort are header
        // items; the job cards follow as list items. Previously this was a
        // non-scrollable Column, which starved the results list of height on
        // phone viewports — clipping filter rows and hiding every job card.
        val success = uiState as? JobsUiState.Success
        // Merged score per job (R-04): ViewModel-computed AI/rule-based fit
        // scores win; a live rule-based computation and the provider-supplied
        // match score cover the window before AI scores land so badges render
        // immediately.
        val mergedScores: Map<String, Int> = success?.jobs?.associate { job ->
            job.id to (success.fitScores[job.id]
                ?: success.careerContext?.profile?.let { JobFitScorer.calculateFitScore(job, it) }
                ?: job.matchScore
                ?: 0)
        } ?: emptyMap()
        val orderedJobs = if (sortByFit) {
            success?.jobs?.sortedByDescending { mergedScores[it.id] ?: 0 } ?: emptyList()
        } else {
            success?.jobs ?: emptyList()
        }

        // Restore the search field from the ViewModel's committed query after
        // process death: the VM restores it from SavedStateHandle, the screen
        // reflects it here. Only syncs when the user hasn't typed something
        // newer locally.
        val committedQuery = success?.filter?.query.orEmpty()
        LaunchedEffect(committedQuery) {
            if (committedQuery.isNotBlank() && searchQuery != committedQuery) {
                searchQuery = committedQuery
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Section: Current Hunt status
            success?.careerContext?.let { context ->
                item(key = "hero") {
                    DiscoveryHeroSection(
                        targetRole = context.profile.targetRole,
                        matchCount = success.jobs.size,
                        onSearchUpdate = { searchQuery = it; viewModel.onEvent(JobsUiEvent.Search(it)) }
                    )
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        Row {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.onEvent(JobsUiEvent.Search(""))
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                                }
                            }
                            IconButton(onClick = {
                                viewModel.onEvent(JobsUiEvent.Search(searchQuery))
                            }) {
                                Icon(
                                    Icons.Rounded.Send,
                                    contentDescription = stringResource(R.string.search),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = AivanceTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { viewModel.onEvent(JobsUiEvent.Search(searchQuery)) }
                    )
                )
            }

            item(key = "filters") {
                JobFilterBar(
                    filter = success?.filter ?: JobSearchFilter(),
                    onFilterChange = { viewModel.onEvent(JobsUiEvent.UpdateFilter(it)) },
                    onClear = { viewModel.onEvent(JobsUiEvent.ClearFilters) }
                )
            }

            item(key = "sort") {
                FitSortRow(
                    sortByFit = sortByFit,
                    onSortChange = { sortByFit = it }
                )
            }

            when (val state = uiState) {
                is JobsUiState.Loading -> item(key = "skeleton") {
                    SkeletonList(itemCount = 6, showAvatar = true)
                }
                is JobsUiState.Success ->
                    if (state.jobs.isEmpty()) {
                        item(key = "empty") {
                            AivanceEmptyState(
                                title = if (state.isSearching) {
                                    stringResource(R.string.no_matches_found)
                                } else {
                                    stringResource(R.string.no_jobs_yet)
                                },
                                description = if (state.isSearching) {
                                    stringResource(R.string.no_matches_desc)
                                } else {
                                    stringResource(R.string.no_jobs_desc)
                                },
                                icon = Icons.Rounded.WorkOff,
                                primaryActionText = stringResource(R.string.refresh),
                                onPrimaryAction = { viewModel.onEvent(JobsUiEvent.Refresh) },
                                secondaryActionText = stringResource(R.string.saved_jobs),
                                onSecondaryAction = onNavigateToSavedJobs
                            )
                        }
                    } else {
                        items(orderedJobs, key = { it.id }) { job ->
                            JobDiscoveryCard(
                                job = job,
                                fitScore = mergedScores[job.id] ?: 0,
                                onClick = { onNavigateToDetails(job.id) },
                                onBookmarkClick = { viewModel.onEvent(JobsUiEvent.ToggleBookmark(job.id)) }
                            )
                        }
                        item(key = "bottom_spacer") { Spacer(Modifier.height(80.dp)) }
                    }
                else -> {}
            }
        }
    }
}

@Composable
private fun FitSortRow(
    sortByFit: Boolean,
    onSortChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = sortByFit,
            onClick = { onSortChange(!sortByFit) },
            label = { Text("Best match") },
            leadingIcon = {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        if (sortByFit) {
            Text(
                text = "Ranked by fit vs. your profile",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiscoveryHeroSection(
    targetRole: String,
    matchCount: Int,
    onSearchUpdate: (String) -> Unit
) {
    AivanceHeroCard(
        title = if (targetRole.isNotBlank()) "Hunting for $targetRole" else "Discovery Hub",
        description = "Found $matchCount active opportunities matching your profile.",
        actionLabel = "Quick Match",
        onClick = { onSearchUpdate(targetRole) }
    )
}

/**
 * Dropdown filter bar for the Job Search tab.
 *
 * Four filter groups:
 *  1. Location — cascading Country → State → City dropdowns.
 *  2. Type — Full Time / Part Time / Internship / Apprenticeship / Contract.
 *  3. Workplace — On Site / Remote / Hybrid.
 *  4. Experience — numeric year buckets (0–2 … 15+).
 */
@Composable
private fun JobFilterBar(
    filter: JobSearchFilter,
    onFilterChange: (JobSearchFilter) -> Unit,
    onClear: () -> Unit
) {
    var countryExpanded by remember { mutableStateOf(false) }
    var stateExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var workplaceExpanded by remember { mutableStateOf(false) }
    var experienceExpanded by remember { mutableStateOf(false) }
    var policyExpanded by remember { mutableStateOf(false) }

    // Catalog tech-stack input (R-02) + apply-assist keyword inputs (R-07):
    // committed on IME search or focus loss so the search pipeline isn't
    // re-run per keystroke.
    var techText by remember { mutableStateOf(filter.technologies.joinToString(", ")) }
    LaunchedEffect(filter.technologies) {
        techText = filter.technologies.joinToString(", ")
    }
    var includeText by remember { mutableStateOf(filter.includedKeywords.joinToString(", ")) }
    LaunchedEffect(filter.includedKeywords) {
        includeText = filter.includedKeywords.joinToString(", ")
    }
    var excludeText by remember { mutableStateOf(filter.excludedKeywords.joinToString(", ")) }
    LaunchedEffect(filter.excludedKeywords) {
        excludeText = filter.excludedKeywords.joinToString(", ")
    }

    fun commitTechStack(raw: String) {
        val parsed = parseKeywords(raw)
        if (parsed != filter.technologies) {
            onFilterChange(filter.copy(technologies = parsed))
        }
    }

    fun commitIncludeKeywords(raw: String) {
        val parsed = parseKeywords(raw)
        if (parsed != filter.includedKeywords) {
            onFilterChange(filter.copy(includedKeywords = parsed))
        }
    }

    fun commitExcludeKeywords(raw: String) {
        val parsed = parseKeywords(raw)
        if (parsed != filter.excludedKeywords) {
            onFilterChange(filter.copy(excludedKeywords = parsed))
        }
    }

    val hasActiveFilters =
        filter.hasStructuredLocation ||
            filter.location.isNotBlank() ||
            filter.remoteType != null ||
            filter.employmentTypes.isNotEmpty() ||
            filter.experienceLevels.isNotEmpty() ||
            filter.minExperienceYears != null ||
            filter.maxExperienceYears != null ||
            filter.remotePolicy != null ||
            filter.technologies.isNotEmpty() ||
            filter.includedKeywords.isNotEmpty() ||
            filter.excludedKeywords.isNotEmpty()

    val employmentOptions = listOf(
        EmploymentType.FULL_TIME, EmploymentType.PART_TIME,
        EmploymentType.INTERNSHIP, EmploymentType.APPRENTICESHIP,
        EmploymentType.CONTRACT
    ).associateWith { it.uiLabel() }

    val remoteOptions = listOf(
        RemoteType.ON_SITE, RemoteType.REMOTE, RemoteType.HYBRID
    ).associateWith { it.uiLabel() }

    val experienceOptions = ExperienceBuckets.options.associateWith { stringResource(it.labelRes) }

    val remotePolicyOptions = listOf(
        RemotePolicy.FULLY_REMOTE, RemotePolicy.REMOTE_FRIENDLY, RemotePolicy.HYBRID
    ).associateWith { it.uiLabel() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdown(
                label = if (filter.country.isNotBlank()) filter.country else stringResource(R.string.country),
                expanded = countryExpanded,
                onExpandedChange = { countryExpanded = it },
                options = LocationCatalog.countryOptions,
                selected = filter.country,
                modifier = Modifier.weight(1f)
            ) { selected ->
                val newState = LocationCatalog.statesFor(selected).firstOrNull().orEmpty()
                val newCity = LocationCatalog.citiesFor(selected, newState).firstOrNull().orEmpty()
                onFilterChange(
                    filter.copy(
                        country = selected,
                        state = if (selected == LocationCatalog.REMOTE) "" else newState,
                        city = if (selected == LocationCatalog.REMOTE) "" else newCity,
                        location = if (selected == LocationCatalog.REMOTE) "Remote" else ""
                    )
                )
            }
            FilterDropdown(
                label = if (filter.state.isNotBlank()) filter.state else stringResource(R.string.state),
                expanded = stateExpanded,
                onExpandedChange = { stateExpanded = it },
                options = LocationCatalog.statesFor(filter.country),
                selected = filter.state,
                modifier = Modifier.weight(1f),
                enabled = filter.country.isNotBlank() && filter.country != LocationCatalog.REMOTE
            ) { selected ->
                val newCity = LocationCatalog.citiesFor(filter.country, selected).firstOrNull().orEmpty()
                onFilterChange(filter.copy(state = selected, city = newCity))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdown(
                label = if (filter.city.isNotBlank()) filter.city else stringResource(R.string.city),
                expanded = cityExpanded,
                onExpandedChange = { cityExpanded = it },
                options = LocationCatalog.citiesFor(filter.country, filter.state),
                selected = filter.city,
                modifier = Modifier.weight(1f),
                enabled = filter.state.isNotBlank()
            ) { selected ->
                onFilterChange(filter.copy(city = selected))
            }
            FilterDropdown(
                label = filter.remoteType?.let { it.uiLabel() } ?: stringResource(R.string.workplace),
                expanded = workplaceExpanded,
                onExpandedChange = { workplaceExpanded = it },
                options = remoteOptions.values.toList(),
                selected = filter.remoteType?.let { it.uiLabel() }.orEmpty(),
                modifier = Modifier.weight(1f)
            ) { selected ->
                val mapped = remoteOptions.entries.firstOrNull { it.value == selected }?.key
                onFilterChange(filter.copy(remoteType = mapped))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdown(
                label = filter.employmentTypes.firstOrNull()?.let { it.uiLabel() }
                    ?: stringResource(R.string.type),
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
                options = employmentOptions.values.toList(),
                selected = filter.employmentTypes.firstOrNull()?.let { it.uiLabel() }.orEmpty(),
                modifier = Modifier.weight(1f)
            ) { selected ->
                val mapped = employmentOptions.entries.firstOrNull { it.value == selected }?.key
                onFilterChange(
                    filter.copy(employmentTypes = if (mapped != null) listOf(mapped) else emptyList())
                )
            }
            FilterDropdown(
                label = experienceLabel(filter),
                expanded = experienceExpanded,
                onExpandedChange = { experienceExpanded = it },
                options = experienceOptions.values.toList(),
                selected = experienceLabel(filter),
                modifier = Modifier.weight(1f)
            ) { selected ->
                val bucket = experienceOptions.entries.firstOrNull { it.value == selected }?.key
                onFilterChange(
                    filter.copy(
                        minExperienceYears = bucket?.min,
                        maxExperienceYears = bucket?.max
                    )
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdown(
                label = filter.remotePolicy?.let { it.uiLabel() } ?: stringResource(R.string.remote_policy),
                expanded = policyExpanded,
                onExpandedChange = { policyExpanded = it },
                options = remotePolicyOptions.values.toList(),
                selected = filter.remotePolicy?.let { it.uiLabel() }.orEmpty(),
                modifier = Modifier.weight(1f)
            ) { selected ->
                val mapped = remotePolicyOptions.entries.firstOrNull { it.value == selected }?.key
                onFilterChange(filter.copy(remotePolicy = mapped))
            }
            OutlinedTextField(
                value = techText,
                onValueChange = { techText = it },
                label = { Text(stringResource(R.string.tech_stack)) },
                placeholder = { Text(stringResource(R.string.tech_stack_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused ->
                        if (!focused.isFocused) commitTechStack(techText)
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { commitTechStack(techText) })
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeywordField(
                value = includeText,
                onValueChange = { includeText = it },
                label = stringResource(R.string.must_include),
                placeholder = stringResource(R.string.must_include_placeholder),
                modifier = Modifier.weight(1f),
                onFocusLost = { commitIncludeKeywords(includeText) },
                onSearch = { commitIncludeKeywords(includeText) }
            )
            KeywordField(
                value = excludeText,
                onValueChange = { excludeText = it },
                label = stringResource(R.string.exclude),
                placeholder = stringResource(R.string.exclude_placeholder),
                modifier = Modifier.weight(1f),
                onFocusLost = { commitExcludeKeywords(excludeText) },
                onSearch = { commitExcludeKeywords(excludeText) }
            )
        }
        if (hasActiveFilters) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Rounded.FilterAltOff, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.clear_all_filters))
                }
            }
        }
    }
}

/** A single Material-3 exposed dropdown used for one filter dimension. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(if (selected.isBlank()) stringResource(R.string.all) else selected) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onExpandedChange(false)
                        onSelect(option)
                    }
                )
            }
        }
    }
}

private data class ExperienceBucket(val labelRes: Int, val min: Int, val max: Int)

private object ExperienceBuckets {
    val options = listOf(
        ExperienceBucket(R.string.exp_0_2, 0, 2),
        ExperienceBucket(R.string.exp_3_5, 3, 5),
        ExperienceBucket(R.string.exp_6_10, 6, 10),
        ExperienceBucket(R.string.exp_11_15, 11, 15),
        ExperienceBucket(R.string.exp_15_plus, 15, 99)
    )
}

@Composable
private fun experienceLabel(filter: JobSearchFilter): String {
    val min = filter.minExperienceYears ?: return stringResource(R.string.experience)
    val max = filter.maxExperienceYears ?: return stringResource(R.string.years_min_plus, min)
    return ExperienceBuckets.options.firstOrNull { it.min == min && it.max == max }?.let {
        stringResource(it.labelRes)
    } ?: stringResource(R.string.years_range, min, max)
}

private fun parseKeywords(raw: String): List<String> =
    raw.split(',', '\n')
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()

/** Single-line keyword input committed on IME search or focus loss (R-07). */
@Composable
private fun KeywordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onFocusLost: () -> Unit,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier.onFocusChanged { focused ->
            if (!focused.isFocused) onFocusLost()
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
private fun RemotePolicy.uiLabel(): String = stringResource(
    when (this) {
        RemotePolicy.FULLY_REMOTE -> R.string.remote_policy_fully_remote
        RemotePolicy.REMOTE_FIRST -> R.string.remote_policy_remote_first
        RemotePolicy.REMOTE_FRIENDLY -> R.string.remote_policy_remote_friendly
        RemotePolicy.HYBRID -> R.string.remote_policy_hybrid
        RemotePolicy.UNKNOWN -> R.string.remote_policy_unknown
    }
)

@Composable
private fun EmploymentType.uiLabel(): String = stringResource(
    when (this) {
        EmploymentType.FULL_TIME -> R.string.employment_full_time
        EmploymentType.PART_TIME -> R.string.employment_part_time
        EmploymentType.INTERNSHIP -> R.string.employment_internship
        EmploymentType.APPRENTICESHIP -> R.string.employment_apprenticeship
        EmploymentType.CONTRACT -> R.string.employment_contract
        EmploymentType.TEMPORARY -> R.string.employment_temporary
        EmploymentType.FREELANCE -> R.string.employment_freelance
        EmploymentType.OTHER -> R.string.employment_other
    }
)

@Composable
private fun RemoteType.uiLabel(): String = stringResource(
    when (this) {
        RemoteType.ON_SITE -> R.string.remote_on_site
        RemoteType.REMOTE -> R.string.remote
        RemoteType.HYBRID -> R.string.remote_hybrid
        RemoteType.OTHER -> R.string.employment_other
    }
)

@Composable
private fun JobDiscoveryCard(
    job: JobListing,
    fitScore: Int,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit
) {
    DashboardCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = AivanceTheme.shapes.medium,
                    color = AivanceTheme.colors.accent.copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(
                            Icons.Rounded.Business,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = AivanceTheme.colors.accent
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        job.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetaText(job.company)
                        if (job.location.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Rounded.LocationOn, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                                MetaText(job.location)
                            }
                        }
                    }
                }

                IconButton(onClick = onBookmarkClick) {
                    Icon(
                        Icons.Rounded.BookmarkBorder,
                        contentDescription = stringResource(R.string.save_job),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Match Intelligence Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Providers rarely supply a match score, so the card shows the
                // merged fit score (LLM-assisted, rule-based fallback — R-04)
                // computed by the ViewModel / discovery list.
                ScoreGauge(score = fitScore, size = 32.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (fitScore > 80) "High Match" else if (fitScore > 50) "Good Match" else "Potential Match",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (fitScore > 80) AivanceTheme.colors.success else AivanceTheme.colors.accent
                    )
                    if (job.experienceLevel != ExperienceLevel.NOT_SPECIFIED) {
                        Text(
                            text = "Matches your ${job.experienceLevel.name.lowercase()} experience.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (job.remoteType == RemoteType.REMOTE || job.isRemote) {
                    StatusChip(text = stringResource(R.string.remote), tone = BannerTone.INFO)
                }
                job.salaryRange?.let {
                    StatusChip(text = it, tone = BannerTone.SUCCESS)
                }
                if (job.employmentType != EmploymentType.FULL_TIME) {
                    StatusChip(text = job.employmentType.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, tone = BannerTone.INFO)
                }
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
