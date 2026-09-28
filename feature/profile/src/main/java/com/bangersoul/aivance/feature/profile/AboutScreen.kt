package com.bangersoul.aivance.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bangersoul.aivance.core.designsystem.components.AivanceTopBar
import com.bangersoul.aivance.core.designsystem.components.DashboardCard
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import com.bangersoul.aivance.core.designsystem.theme.DarkAccent

/**
 * About AiVance — contact, licenses, and the story of how the app is made.
 * Accessible from Settings → About AiVance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        AivanceTopBar(title = stringResource(R.string.about_title), onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Brand header with the startup quote ──────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(DarkAccent.copy(alpha = 0.85f), Color(0xFF0B1220))
                            )
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.about_brand),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.about_quote),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.version_placeholder),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            // ── Contact ──────────────────────────────────────────────────
            item { AboutSectionHeader(stringResource(R.string.connect_creator)) }
            item {
                ContactRow(
                    icon = Icons.Rounded.Email,
                    title = stringResource(R.string.contact_email_title),
                    subtitle = "iamshaikhazmathulla@outlook.com",
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.fromParts("mailto", "iamshaikhazmathulla@outlook.com", null)
                        )
                        context.startActivity(intent)
                    }
                )
            }
            item {
                ContactRow(
                    icon = Icons.Rounded.CameraAlt,
                    title = stringResource(R.string.contact_instagram_title),
                    subtitle = "@Iamazmathulla",
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/Iamazmathulla"))
                            )
                        } catch (_: Exception) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/Iamazmathulla"))
                            )
                        }
                    }
                )
            }

            // ── How AiVance is made ──────────────────────────────────────
            item { AboutSectionHeader(stringResource(R.string.how_made)) }
            item {
                DashboardCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TechRow(
                            icon = Icons.Rounded.Terminal,
                            title = stringResource(R.string.tech_native_android),
                            subtitle = stringResource(R.string.tech_native_android_sub)
                        )
                        TechRow(
                            icon = Icons.Rounded.Memory,
                            title = stringResource(R.string.tech_ai_engines),
                            subtitle = stringResource(R.string.tech_ai_engines_sub)
                        )
                        TechRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = stringResource(R.string.tech_career_intelligence),
                            subtitle = stringResource(R.string.tech_career_intelligence_sub)
                        )
                        Text(
                            stringResource(R.string.about_architecture),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Licenses ─────────────────────────────────────────────────
            item { AboutSectionHeader(stringResource(R.string.open_source_licenses)) }
            item {
                LicenseRow(stringResource(R.string.license_androidx), stringResource(R.string.license_apache), "https://www.apache.org/licenses/LICENSE-2.0.html")
            }
            item {
                LicenseRow(stringResource(R.string.license_kotlin), stringResource(R.string.license_apache), "https://www.apache.org/licenses/LICENSE-2.0.html")
            }
            item {
                LicenseRow(stringResource(R.string.license_material), stringResource(R.string.license_apache), "https://www.apache.org/licenses/LICENSE-2.0.html")
            }
            item {
                LicenseRow(stringResource(R.string.license_okhttp), stringResource(R.string.license_apache), "https://www.apache.org/licenses/LICENSE-2.0.html")
            }
            item {
                LicenseRow(stringResource(R.string.license_room), stringResource(R.string.license_apache), "https://www.apache.org/licenses/LICENSE-2.0.html")
            }
            item {
                LicenseRow(stringResource(R.string.license_coil), stringResource(R.string.license_apache), "https://www.apache.org/licenses/LICENSE-2.0.html")
            }
            item {
                LicenseRow(stringResource(R.string.license_workmanager), stringResource(R.string.license_apache), "https://www.apache.org/licenses/LICENSE-2.0.html")
            }
            item {
                LicenseRow(stringResource(R.string.license_provider_sdks), stringResource(R.string.license_provider_terms), "https://groq.com/")
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.made_with_care),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AboutSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ContactRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    DashboardCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = AivanceTheme.colors.accent.copy(alpha = 0.12f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = AivanceTheme.colors.accent
                )
            }
            Column(modifier = Modifier.weight(1f)) {                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TechRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = AivanceTheme.shapes.medium,
            color = AivanceTheme.colors.info.copy(alpha = 0.12f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(20.dp),
                tint = AivanceTheme.colors.info
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LicenseRow(name: String, license: String, url: String) {
    val context = LocalContext.current
    DashboardCard(onClick = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // No browser available — ignore
        }
    }, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = AivanceTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    stringResource(R.string.view_license),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
