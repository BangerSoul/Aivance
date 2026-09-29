package com.bangersoul.aivance.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.designsystem.components.AivanceScreen
import com.bangersoul.aivance.core.designsystem.components.DashboardCard

/**
 * One curated remote-work resource (R-06). The catalog is static reference
 * content — titles and descriptions are proper-noun platform names that don't
 * translate — while the screen chrome and category headers are localized.
 */
data class ResourceLink(
    val title: String,
    val description: String,
    val url: String,
    val category: String
)

object ResourceHubCatalog {

    /** Category key → localized label resource, in display order. */
    val categories: List<Pair<String, Int>> = listOf(
        "Job Boards" to R.string.resources_cat_job_boards,
        "Curated Lists" to R.string.resources_cat_curated,
        "Interview Prep" to R.string.resources_cat_prep,
        "Remote Companies" to R.string.resources_cat_companies
    )

    val resources = listOf(
        ResourceLink("RemoteOK", "Global remote tech jobs & developer roles", "https://remoteok.com", "Job Boards"),
        ResourceLink("Remotive", "Hand-screened remote jobs in tech, design, marketing", "https://remotive.com", "Job Boards"),
        ResourceLink("Jobicy", "Free global remote job search engine", "https://jobicy.com", "Job Boards"),
        ResourceLink("Arbeitnow", "Germany & European remote-friendly job board", "https://arbeitnow.com", "Job Boards"),
        ResourceLink("USAJobs", "US federal government careers", "https://www.usajobs.gov", "Job Boards"),
        ResourceLink("Awesome Remote Jobs", "Community-curated remote work resources", "https://github.com/lukasz-madon/awesome-remote-job", "Curated Lists"),
        ResourceLink("Remoteintech", "Directory of remote-friendly tech companies (the AiVance catalog source)", "https://remoteintech.org", "Curated Lists"),
        ResourceLink("Tech Interview Handbook", "Free curated guide for coding & system design interviews", "https://techinterviewhandbook.org", "Interview Prep"),
        ResourceLink("System Design Primer", "Learn how to design large-scale systems", "https://github.com/donnemartin/system-design-primer", "Interview Prep"),
        ResourceLink("Automattic", "Distributed-first — WordPress.com, WooCommerce and more", "https://automattic.com/work-with-us/", "Remote Companies"),
        ResourceLink("GitLab", "All-remote with a public company handbook", "https://about.gitlab.com/jobs/", "Remote Companies"),
        ResourceLink("Zapier", "Remote-first automation platform", "https://zapier.com/jobs/", "Remote Companies"),
        ResourceLink("Shopify", "Remote-friendly commerce platform", "https://www.shopify.com/careers", "Remote Companies")
    )

    fun resourcesFor(category: String): List<ResourceLink> = resources.filter { it.category == category }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteResourcesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    AivanceScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.resources_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WorkOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.resources_hub_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.resources_hub_subtitle), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            ResourceHubCatalog.categories.forEach { (category, labelRes) ->
                val items = ResourceHubCatalog.resourcesFor(category)
                if (items.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(items, key = { it.url }) { item ->
                        DashboardCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                                } catch (_: Exception) {
                                    // No browser available — ignore
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(item.description, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.open_link), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
