package com.bangersoul.aivance.feature.jobs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.common.enums.RemotePolicy
import com.bangersoul.aivance.core.common.model.CompanyCatalogEntry
import com.bangersoul.aivance.core.designsystem.components.AivanceEmptyState
import com.bangersoul.aivance.core.designsystem.components.AivanceError
import com.bangersoul.aivance.core.designsystem.components.ActionButton
import com.bangersoul.aivance.core.designsystem.components.AivanceScreen
import com.bangersoul.aivance.core.designsystem.components.DashboardCard
import com.bangersoul.aivance.core.designsystem.components.SkeletonDashboard
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyDetailScreen(
    viewModel: CompanyDetailViewModel,
    companyId: String,
    onBack: () -> Unit,
    onNavigateToRecruiters: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(companyId) {
        viewModel.load(companyId)
    }

    AivanceScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.company_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "CompanyDetailTransition"
        ) { state ->
            when (state) {
                is CompanyDetailUiState.Loading -> SkeletonDashboard(modifier = Modifier.fillMaxSize())
                is CompanyDetailUiState.Error -> AivanceError(
                    message = state.message,
                    onRetry = { viewModel.load(companyId) }
                )
                is CompanyDetailUiState.Success -> CompanyContent(
                    state = state,
                    companyId = companyId,
                    onNavigateToRecruiters = onNavigateToRecruiters
                )
            }
        }
    }
}

@Composable
private fun CompanyContent(
    state: CompanyDetailUiState.Success,
    companyId: String,
    onNavigateToRecruiters: (String) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DashboardCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(state.companyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (state.location.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(state.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Remote-company catalog facts (R-02): policy badge, size, region, tech
        // stack and careers page — only when the company is indexed.
        state.catalog?.let { catalog ->
            item {
                CompanyCatalogCard(
                    catalog = catalog,
                    onOpenCareers = { url -> openExternalUrl(context, url) }
                )
            }
        }

        // Find Recruiters — targets the first open role at this company, falling
        // back to the company id itself when no indexed roles exist.
        item {
            ActionButton(
                text = stringResource(R.string.find_recruiters),
                onClick = {
                    val targetId = state.openRoles.firstOrNull()?.id ?: companyId
                    onNavigateToRecruiters(targetId)
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.PersonSearch
            )
        }

        item {
            Text(
                if (state.openRoles.isEmpty()) {
                    stringResource(R.string.open_roles)
                } else {
                    stringResource(R.string.open_roles_count, state.openRoles.size)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (state.openRoles.isEmpty()) {
            item {
                AivanceEmptyState(
                    title = stringResource(R.string.no_open_roles),
                    description = stringResource(R.string.no_open_roles_desc),
                    icon = Icons.Rounded.WorkOutline
                )
            }
        } else {
            items(state.openRoles, key = { it.id }) { job ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(job.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.LocationOn, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(job.location.ifBlank { stringResource(R.string.remote) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Renders the bundled catalog entry for a company (R-02): remote-policy badge,
 * size/region facts, technology chips and the careers page action.
 */
@Composable
private fun CompanyCatalogCard(
    catalog: CompanyCatalogEntry,
    onOpenCareers: (String) -> Unit
) {
    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    catalog.policy.uiLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.company_catalog_available),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (catalog.companySize != null || catalog.region != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    catalog.companySize?.let { size ->
                        CatalogFact(
                            label = stringResource(R.string.company_company_size),
                            value = size
                        )
                    }
                    catalog.region?.let { region ->
                        CatalogFact(
                            label = stringResource(R.string.company_region),
                            value = region
                        )
                    }
                }
            }

            if (catalog.technologies.isNotEmpty()) {
                Text(
                    stringResource(R.string.company_technologies),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    catalog.technologies.take(12).forEach { tech ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                tech,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            val careersUrl = catalog.careersUrl
            if (!careersUrl.isNullOrBlank()) {
                TextButton(
                    onClick = { onOpenCareers(careersUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.company_careers))
                }
            }
        }
    }
}

@Composable
private fun CatalogFact(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
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

private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
