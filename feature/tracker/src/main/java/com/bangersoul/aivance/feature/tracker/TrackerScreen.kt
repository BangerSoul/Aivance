package com.bangersoul.aivance.feature.tracker

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.model.ApplicationStage
import com.bangersoul.aivance.core.common.model.TimelineEvent
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackerScreen(
    viewModel: TrackerViewModel,
    onBack: () -> Unit,
    initialJobId: String? = null,
    onNavigateToAnalytics: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Cross-feature jump (e.g. saved job's "Track application"): pre-select the
    // job on first arrival.
    LaunchedEffect(initialJobId) {
        if (!initialJobId.isNullOrBlank()) {
            viewModel.onEvent(TrackerUiEvent.TrackJob(initialJobId))
        }
    }

    val pendingTrackJob = (uiState as? TrackerUiState.Success)?.pendingTrackJob
    var showAddDialog by remember { mutableStateOf(false) }

    // When a job arrives from another feature, surface the Add dialog pre-filled
    // with its company/role so one tap adds it to the pipeline.
    LaunchedEffect(pendingTrackJob) {
        if (pendingTrackJob != null) {
            showAddDialog = true
        }
    }

    AivanceWorkspaceScaffold(
        title = stringResource(R.string.career_pipeline_title),
        subtitle = stringResource(R.string.tracker_subtitle),
        backContentDescription = stringResource(R.string.back),
        onBack = onBack,
        isLoading = uiState is TrackerUiState.Loading,
        error = (uiState as? TrackerUiState.Error)?.message,
        onRetry = { viewModel.onEvent(TrackerUiEvent.Refresh) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_application))
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TrackerTransition"
            ) { state ->
                when (state) {
                    is TrackerUiState.Success -> PipelineContent(
                        stages = state.stages,
                        applications = state.applications,
                        metrics = state.pipelineMetrics,
                        todayAppliedCount = state.todayAppliedCount,
                        dailyCap = state.dailyCap,
                        onSetDailyCap = { cap ->
                            viewModel.onEvent(TrackerUiEvent.SetDailyCap(cap))
                        },
                        selectedApplicationId = state.selectedApplicationId,
                        onMove = { appId, stageId ->
                            viewModel.onEvent(TrackerUiEvent.UpdateStage(appId, stageId))
                        },
                        onSelect = { appId ->
                            viewModel.onEvent(TrackerUiEvent.SelectApplication(appId))
                        },
                        onClose = { viewModel.onEvent(TrackerUiEvent.CloseApplication) },
                        onDelete = { appId ->
                            viewModel.onEvent(TrackerUiEvent.DeleteApplication(appId))
                        },
                        onNotesChange = { appId, notes ->
                            viewModel.onEvent(TrackerUiEvent.UpdateNotes(appId, notes))
                        },
                        onNavigateToAnalytics = onNavigateToAnalytics
                    )
                    else -> {}
                }
            }
        }
    }

    if (showAddDialog) {
        val stages = (uiState as? TrackerUiState.Success)?.stages ?: emptyList()
        AddApplicationDialog(
            stages = stages,
            initialCompany = pendingTrackJob?.company.orEmpty(),
            initialRole = pendingTrackJob?.title.orEmpty(),
            onDismiss = {
                showAddDialog = false
                if (pendingTrackJob != null) {
                    viewModel.onEvent(TrackerUiEvent.ClearPendingTrackJob)
                }
            },
            onAdd = { company, role, stageId ->
                showAddDialog = false
                if (pendingTrackJob != null) {
                    viewModel.onEvent(TrackerUiEvent.ClearPendingTrackJob)
                }
                viewModel.onEvent(TrackerUiEvent.AddApplication(company, role, stageId))
            }
        )
    }
}

@Composable
private fun PipelineContent(
    stages: List<ApplicationStage>,
    applications: List<Application>,
    metrics: PipelineMetrics,
    todayAppliedCount: Int,
    dailyCap: Int,
    onSetDailyCap: (Int) -> Unit,
    selectedApplicationId: Long?,
    onMove: (Long, String) -> Unit,
    onSelect: (Long) -> Unit,
    onClose: () -> Unit,
    onDelete: (Long) -> Unit,
    onNotesChange: (Long, String) -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    var showCapDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Hero Section
        Column(modifier = Modifier.padding(16.dp)) {
            AivanceHeroCard(
                title = "Pipeline Performance",
                description = metrics.interviewRate?.let {
                    "You have ${metrics.activeCount} active applications. Your interview conversion is $it%."
                } ?: "You have ${metrics.activeCount} active applications. Apply to a role to start tracking your interview conversion.",
                actionLabel = "View Analytics",
                onClick = onNavigateToAnalytics
            )
            Spacer(Modifier.height(12.dp))
            DailyQuotaCard(
                todayAppliedCount = todayAppliedCount,
                dailyCap = dailyCap,
                onEditCap = { showCapDialog = true }
            )
            Spacer(Modifier.height(16.dp))
            SectionHeader(title = "Kanban Board")
        }

        PipelineBoard(
            stages = stages,
            applications = applications,
            selectedApplicationId = selectedApplicationId,
            onMove = onMove,
            onSelect = onSelect,
            onClose = onClose,
            onDelete = onDelete,
            onNotesChange = onNotesChange
        )
    }

    if (showCapDialog) {
        DailyCapDialog(
            currentCap = dailyCap,
            onDismiss = { showCapDialog = false },
            onSelect = { cap ->
                showCapDialog = false
                onSetDailyCap(cap)
            }
        )
    }
}

/**
 * Daily application quota (R-07): today's count vs. the configurable cap, with
 * a warning tint when the cap is reached or exceeded.
 */
@Composable
private fun DailyQuotaCard(
    todayAppliedCount: Int,
    dailyCap: Int,
    onEditCap: () -> Unit
) {
    val over = todayAppliedCount >= dailyCap
    val progress = (todayAppliedCount.toFloat() / dailyCap.coerceAtLeast(1)).coerceIn(0f, 1f)

    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.tracker_daily_quota), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.tracker_quota_progress, todayAppliedCount, dailyCap),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onEditCap) {
                    Text(stringResource(R.string.tracker_edit_cap))
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (over) MaterialTheme.colorScheme.error else AivanceTheme.colors.accent
            )
        }
    }
}

/** Lets the user pick a daily application cap from presets (R-07). */
@Composable
private fun DailyCapDialog(
    currentCap: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val presets = listOf(3, 5, 10, 15, 20)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracker_daily_cap)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.tracker_daily_cap_detail),
                    style = MaterialTheme.typography.bodyMedium
                )
                presets.forEach { preset ->
                    val selected = preset == currentCap
                    FilterChip(
                        selected = selected,
                        onClick = { onSelect(preset) },
                        label = { Text(if (selected) stringResource(R.string.tracker_preset_per_day_selected, preset) else stringResource(R.string.tracker_preset_per_day, preset)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Dialog to manually add a job application: company, role, and pipeline stage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddApplicationDialog(
    stages: List<ApplicationStage>,
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit,
    initialCompany: String = "",
    initialRole: String = ""
) {
    var company by remember { mutableStateOf(initialCompany) }
    var role by remember { mutableStateOf(initialRole) }
    var selectedStageId by remember { mutableStateOf(stages.firstOrNull()?.id ?: "SAVED") }
    var stageExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_application_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text(stringResource(R.string.company)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text(stringResource(R.string.role_job_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = stageExpanded,
                    onExpandedChange = { stageExpanded = it }
                ) {
                    OutlinedTextField(
                        value = stages.firstOrNull { it.id == selectedStageId }?.label ?: stringResource(R.string.saved),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.stage)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stageExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = stageExpanded,
                        onDismissRequest = { stageExpanded = false }
                    ) {
                        stages.forEach { stage ->
                            DropdownMenuItem(
                                text = { Text(stage.label) },
                                onClick = {
                                    selectedStageId = stage.id
                                    stageExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(company, role, selectedStageId) },
                enabled = company.isNotBlank() && role.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun PipelineBoard(
    stages: List<ApplicationStage>,
    applications: List<Application>,
    selectedApplicationId: Long?,
    onMove: (Long, String) -> Unit,
    onSelect: (Long) -> Unit,
    onClose: () -> Unit,
    onDelete: (Long) -> Unit,
    onNotesChange: (Long, String) -> Unit
) {
    if (stages.isEmpty()) {
        AivanceEmptyState(
            title = stringResource(R.string.no_pipeline_stages),
            description = stringResource(R.string.no_pipeline_stages_desc),
            icon = Icons.Rounded.ViewKanban
        )
        return
    }

    val selectedApplication = selectedApplicationId?.let { id ->
        applications.firstOrNull { it.id == id }
    }

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(stages) { stage ->
            PipelineColumn(
                stage = stage,
                applications = applications.filter { it.currentStageId == stage.id },
                onMove = onMove,
                onSelect = onSelect
            )
        }
    }

    if (selectedApplication != null) {
        ApplicationDetailSheet(
            application = selectedApplication,
            onDismiss = onClose,
            onDelete = { onDelete(selectedApplication.id) },
            onNotesChange = { onNotesChange(selectedApplication.id, it) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PipelineColumn(
    stage: ApplicationStage,
    applications: List<Application>,
    onMove: (Long, String) -> Unit,
    onSelect: (Long) -> Unit
) {
    var isDropTarget by remember { mutableStateOf(false) }

    Column(modifier = Modifier.width(288.dp)) {
        // Column header with count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(stageColor(stage.id), RoundedCornerShape(3.dp))
                )
                Text(stage.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Text(applications.size.toString(), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        val currentOnMove by rememberUpdatedState(onMove)
        val dropTarget = remember(stage.id) {
            object : DragAndDropTarget {
                override fun onStarted(event: DragAndDropEvent) {
                    isDropTarget = true
                }

                override fun onEntered(event: DragAndDropEvent) {
                    isDropTarget = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    isDropTarget = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    isDropTarget = false
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    isDropTarget = false
                    val appId = event.toAndroidDragEvent()
                        ?.clipData?.getItemAt(0)?.text?.toString()?.toLongOrNull()
                    return if (appId != null) {
                        currentOnMove(appId, stage.id)
                        true
                    } else {
                        false
                    }
                }
            }
        }

        // Drop target surface wrapping the card list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        event.toAndroidDragEvent()?.clipData
                            ?.let { it.getItemAt(0)?.text?.isNotBlank() == true } ?: false
                    },
                    target = dropTarget
                )
                .dropHighlight(isDropTarget)
        ) {
            if (applications.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.no_applications),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(applications, key = { it.id }) { app ->
                        val currentOnSelect by rememberUpdatedState(onSelect)
                        KanbanCard(
                            app = app,
                            onClick = { currentOnSelect(app.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.dropHighlight(isActive: Boolean): Modifier {
    val targetAlpha by animateFloatAsState(if (isActive) 1f else 0f, label = "dropTarget")
    return this.then(
        Modifier.background(
            AivanceTheme.colors.accent.copy(alpha = 0.16f * targetAlpha),
            RoundedCornerShape(16.dp)
        )
    )
}

@Composable
private fun KanbanCard(
    app: Application,
    onClick: () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    DashboardCard(
        modifier = Modifier
            .fillMaxWidth()
            // A plain tap opens the application detail sheet.
            .clickable { currentOnClick() }
            // Foundation 1.11's drag-and-drop API: the source is draggable
            // (long-press to lift) whenever transferData is non-null.
            .dragAndDropSource(
                transferData = { _ ->
                    DragAndDropTransferData(
                        ClipData.newPlainText("application", app.id.toString())
                    )
                }
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                app.job?.title ?: stringResource(R.string.unknown_role),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Business, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(
                    app.job?.company ?: stringResource(R.string.unknown_company),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }

            if (app.atsReportId != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Description, null, Modifier.size(12.dp), tint = AivanceTheme.colors.success)
                    Text(stringResource(R.string.ats_optimized), style = MaterialTheme.typography.labelSmall, color = AivanceTheme.colors.success)
                }
            }

            if (app.tasks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val openTasks = app.tasks.count { !it.isCompleted }
                Text(
                    stringResource(R.string.open_tasks, openTasks, if (openTasks == 1) "" else "s"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun stageColor(stageId: String): Color = when (stageId.uppercase()) {
    "SAVED", "PREPARING" -> AivanceTheme.colors.info
    "APPLIED", "ASSESSMENT" -> AivanceTheme.colors.accent
    "INTERVIEW", "INTERVIEWING" -> AivanceTheme.colors.warning
    "OFFER" -> AivanceTheme.colors.success
    "REJECTED", "CLOSED" -> MaterialTheme.colorScheme.error
    "ARCHIVED" -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.secondary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplicationDetailSheet(
    application: Application,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onNotesChange: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.9f)) {
            // Workspace Header
            ApplicationWorkspaceHeader(application)

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text(stringResource(R.string.tracker_overview), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text(stringResource(R.string.tracker_tasks), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge)
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Text(stringResource(R.string.tracker_timeline), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge)
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TabContent"
                ) { tab ->
                    when (tab) {
                        0 -> ApplicationOverviewTab(application, onNotesChange, onDelete = { showDeleteConfirm = true })
                        1 -> ApplicationTasksTab(application)
                        2 -> ApplicationTimelineTab(application)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(application, onDelete, onDismiss = { showDeleteConfirm = false })
    }
}

@Composable
private fun ApplicationWorkspaceHeader(application: Application) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = stageColor(application.currentStageId).copy(alpha = 0.1f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Business, null, tint = stageColor(application.currentStageId))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(application.job?.title ?: "Unknown Role", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(application.job?.company ?: "Unknown Company", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusChip(text = application.currentStageId, tone = BannerTone.INFO)
    }
}

@Composable
private fun ApplicationOverviewTab(
    application: Application,
    onNotesChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var notesText by remember(application.id) { mutableStateOf(application.notes.orEmpty()) }

    LaunchedEffect(notesText) {
        if (notesText != application.notes.orEmpty()) {
            kotlinx.coroutines.delay(600)
            onNotesChange(notesText)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(label = "ATS Match", value = "${application.atsReportId?.let { 85 } ?: 0}%", icon = Icons.Rounded.Analytics, modifier = Modifier.weight(1f))
                StatCard(label = "Priority", value = "High", icon = Icons.Rounded.Flag, modifier = Modifier.weight(1f))
            }
        }

        item {
            Text(stringResource(R.string.tracker_notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.tracker_notes_placeholder)) },
                minLines = 4,
                shape = AivanceTheme.shapes.medium
            )
        }

        item {
            val jobUrl = application.job?.url
            if (!jobUrl.isNullOrBlank()) {
                AivanceSecondaryButton(
                    text = stringResource(R.string.tracker_open_listing),
                    onClick = { uriHandler.openUri(jobUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.OpenInNew
                )
            }
        }

        item {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tracker_delete_application))
            }
        }
    }
}

@Composable
private fun ApplicationTasksTab(application: Application) {
    if (application.tasks.isEmpty()) {
        AivanceEmptyState(
            title = stringResource(R.string.tracker_no_tasks),
            description = stringResource(R.string.tracker_no_tasks_detail),
            icon = Icons.Rounded.AssignmentTurnedIn
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(application.tasks) { task ->
                TaskRow(task)
            }
        }
    }
}

@Composable
private fun TaskRow(task: com.bangersoul.aivance.core.common.model.ApplicationTask) {
    AivanceWorkspaceCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { /* Update Task */ })
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(task.description.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.priority == "HIGH") {
                Icon(Icons.Rounded.PriorityHigh, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ApplicationTimelineTab(application: Application) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (application.timeline.isEmpty()) {
            item {
                Text(stringResource(R.string.tracker_no_activity), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            items(application.timeline) { event ->
                TimelineRow(event)
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(application: Application, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracker_delete_application)) },
        text = { Text(stringResource(R.string.tracker_delete_detail, application.job?.title ?: "", application.job?.company ?: "")) },
        confirmButton = {
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = AivanceTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimelineRow(event: TimelineEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(AivanceTheme.colors.accent, RoundedCornerShape(4.dp))
                .padding(top = 20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                event.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!event.description.isNullOrBlank()) {
                Text(
                    event.description.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                formatTimestamp(event.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    return try {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        ""
    }
}
