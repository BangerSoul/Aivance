package com.bangersoul.aivance.feature.jobs

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bangersoul.aivance.core.common.model.Recruiter
import com.bangersoul.aivance.core.designsystem.components.*
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

/**
 * In-app apply surface. Hosts the real external apply page in a hardened
 * [WebView] while an AI suggestions bottom sheet offers an ATS score, a
 * generated cover letter, and recruiter emails so the user never has to leave
 * the app to apply.
 *
 * Security: the WebView loads third-party pages with JavaScript enabled (many
 * ATS portals require it), so it is locked down — file/content access disabled,
 * no universal access from file URLs, and only https(s) apply navigations are
 * kept in-app; anything else (mailto:, tel:, app deep links) is handed to the
 * system so the WebView never becomes an open redirect surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplyBrowserScreen(
    viewModel: ApplyBrowserViewModel,
    jobId: String,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAssistant by remember { mutableStateOf(false) }

    // The WebView is created once and remembered so the loaded apply page and
    // its form state survive recomposition (e.g. opening the assistant sheet).
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(jobId) { viewModel.load(jobId) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ApplyBrowserUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is ApplyBrowserUiEffect.ShowUndoableSnackbar -> {
                    // Dismiss any in-flight snackbar so the actionable one isn't
                    // queued behind it and the Undo window starts immediately.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        withDismissAction = true,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoTracking()
                    }
                }
                is ApplyBrowserUiEffect.CopyText -> clipboard.setText(AnnotatedString(effect.text))
                is ApplyBrowserUiEffect.OpenExternalUrl -> openExternally(context, effect.url)
            }
        }
    }

    AivanceWorkspaceScaffold(
        title = uiState.job?.title ?: stringResource(R.string.apply_title),
        subtitle = uiState.job?.company,
        onBack = onNavigateBack,
        showAssistantAction = false,
        topBarActions = {
            IconButton(
                onClick = { webView?.reload() },
                enabled = uiState.applyUrl != null
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.apply_reload_page))
            }
            IconButton(
                onClick = { viewModel.openExternal() },
                enabled = uiState.applyUrl != null
            ) {
                Icon(Icons.Rounded.OpenInBrowser, contentDescription = stringResource(R.string.apply_open_external))
            }
        },
        isLoading = uiState.isLoading,
        error = uiState.error,
        onRetry = { viewModel.load(jobId) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState.applyUrl != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAssistant = true },
                    icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    text = { Text(stringResource(R.string.apply_ai_assist)) }
                )
            }
        }
    ) {
        val applyUrl = uiState.applyUrl
        if (applyUrl == null && !uiState.isLoading && uiState.error == null) {
            AivanceEmptyState(
                title = stringResource(R.string.apply_no_link),
                description = stringResource(R.string.apply_ai_assist_desc),
                icon = Icons.Rounded.LinkOff
            )
        } else if (applyUrl != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                HardenedApplyWebView(
                    url = applyUrl,
                    onWebViewCreated = { webView = it },
                    onOpenExternally = { url -> openExternally(context, url) },
                    onSubmissionDetected = viewModel::onApplicationSubmitted,
                    modifier = Modifier.fillMaxSize()
                )
                // Persistent confirmation once the submission has been recorded
                // in the Pipeline. Overlaid so it never reflows the apply page.
                if (uiState.applicationTracked) {
                    ApplicationTrackedChip(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                    )
                }
            }
        }
    }

    if (showAssistant) {
        ModalBottomSheet(
            onDismissRequest = { showAssistant = false },
            sheetState = sheetState
        ) {
            ApplyAssistantPanel(
                state = uiState,
                onRunAts = viewModel::runAtsScore,
                onGenerateCover = viewModel::generateCoverLetter,
                onCopyCover = viewModel::copyCoverLetter,
                onFindRecruiters = viewModel::findRecruiters,
                onCopyEmail = viewModel::copyRecruiterEmail
            )
        }
    }
}

/**
 * Name the [ApplySubmitBridge] is exposed under to page JavaScript. Kept short
 * and app-specific so it doesn't collide with page globals.
 */
private const val SUBMIT_BRIDGE_NAME = "AivanceApply"

/**
 * Injected once per page load. Hooks the capture-phase `submit` event (covers
 * classic form posts) and clicks on submit-like controls (covers SPA/XHR apply
 * flows on Greenhouse/Workday-style portals that never fire a real `submit`),
 * calling back into the native bridge. Guarded so repeated injection on the
 * same document is a no-op.
 */
private val SUBMIT_LISTENER_JS = """
(function() {
  if (window.__aivanceSubmitHooked) { return; }
  window.__aivanceSubmitHooked = true;
  document.addEventListener('submit', function() {
    try { $SUBMIT_BRIDGE_NAME.onFormSubmit(); } catch (e) {}
  }, true);
  document.addEventListener('click', function(ev) {
    var el = ev.target && ev.target.closest
      ? ev.target.closest('button, input[type=submit], [role=button], a')
      : null;
    if (!el) { return; }
    var label = (el.innerText || el.value || el.getAttribute('aria-label') || '').toLowerCase();
    if (/(submit application|submit your application|send application|apply now|submit)/.test(label)) {
      try { $SUBMIT_BRIDGE_NAME.onSubmitIntent(); } catch (e) {}
    }
  }, true);
})();
""".trimIndent()

/**
 * Heuristic matcher for confirmation/thank-you landing pages that ATS portals
 * redirect to after a successful submission. Combined with the JS submit hook
 * and the ViewModel's idempotency guard, so an over-match here only risks
 * tracking the very job the user opened — never a spurious external effect.
 */
private val CONFIRMATION_URL_REGEX = Regex(
    "thank[-_]?you|confirmation|/confirm|application[-_]?(submitted|complete|received)|/submitted|/success|apply/complete",
    RegexOption.IGNORE_CASE
)

/**
 * Minimal JS→native bridge exposing only two zero-arg callbacks. No reflection
 * surface and no data crosses the boundary, so a hostile page can at most
 * trigger a Pipeline entry for the job already on screen. Bridge methods run on
 * a background JS thread; callers marshal back to the UI thread.
 */
private class ApplySubmitBridge(private val onSubmit: () -> Unit) {
    @JavascriptInterface
    fun onFormSubmit() = onSubmit()

    @JavascriptInterface
    fun onSubmitIntent() = onSubmit()
}

/**
 * Persistent "Tracked in Pipeline" confirmation shown once the apply-page
 * submission has been recorded as an Application at the APPLIED stage. Uses the
 * design system success tone so it reads as a positive, terminal state.
 */
@Composable
private fun ApplicationTrackedChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = AivanceTheme.colors.successContainer,
        contentColor = AivanceTheme.colors.onSuccessContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = AivanceTheme.colors.success
            )
            Text(
                stringResource(R.string.apply_tracked),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Builds a [WebView] locked down for loading untrusted third-party apply pages.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun HardenedApplyWebView(
    url: String,
    onWebViewCreated: (WebView) -> Unit,
    onOpenExternally: (String) -> Unit,
    onSubmissionDetected: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep a stable reference so the JS bridge and page callbacks always reach
    // the latest callback without recreating the WebView.
    val currentOnSubmit by rememberUpdatedState(onSubmissionDetected)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val target = request.url
                        val scheme = target.scheme?.lowercase()
                        // Keep only https(s) apply navigations in-app. Anything
                        // else (mailto:, tel:, intent:, custom app links) is a
                        // potential redirect/escape vector, so hand it to the
                        // system and refuse to load it in the WebView.
                        return if (scheme == "http" || scheme == "https") {
                            false
                        } else {
                            onOpenExternally(target.toString())
                            true
                        }
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        // A redirect to a confirmation/thank-you page is a strong
                        // signal the submission went through, even when no JS
                        // `submit` event fired (SPA/XHR portals). Runs on the UI
                        // thread already.
                        if (url != null && CONFIRMATION_URL_REGEX.containsMatchIn(url)) {
                            currentOnSubmit()
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        // Re-inject the submit hook on every finished navigation
                        // so client-side route changes stay covered.
                        view.evaluateJavascript(SUBMIT_LISTENER_JS, null)
                    }
                }
                addJavascriptInterface(
                    // Bridge callbacks fire on a background JS thread; hop back to
                    // the WebView's UI thread before touching the ViewModel.
                    ApplySubmitBridge { post { currentOnSubmit() } },
                    SUBMIT_BRIDGE_NAME
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // Harden against local-file exfiltration by malicious pages.
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                }
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { view ->
            // Reload only when the resolved apply URL actually changes, so
            // recomposition never blows away in-progress form input.
            if (view.url != url && view.originalUrl != url) {
                view.loadUrl(url)
            }
        }
    )
}

@Composable
private fun ApplyAssistantPanel(
    state: ApplyBrowserUiState,
    onRunAts: () -> Unit,
    onGenerateCover: () -> Unit,
    onCopyCover: () -> Unit,
    onFindRecruiters: () -> Unit,
    onCopyEmail: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            stringResource(R.string.apply_ai_assist),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.apply_ai_assist_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AtsSection(state.ats, onRunAts)
        CoverLetterSection(state.coverLetter, onGenerateCover, onCopyCover)
        RecruiterSection(state.recruiters, onFindRecruiters, onCopyEmail)
    }
}

@Composable
private fun AssistCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AivanceTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
            Icon(icon, null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AtsSection(state: AtsSuggestionState, onRun: () -> Unit) {
    AssistCard {
        SectionTitle(Icons.Rounded.Assessment, stringResource(R.string.apply_section_ats))
        Spacer(Modifier.height(12.dp))
        when (state) {
            AtsSuggestionState.Idle -> {
                AivancePrimaryButton(
                    text = stringResource(R.string.apply_ats_run),
                    onClick = onRun,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Search
                )
            }
            is AtsSuggestionState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.apply_ats_running), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.streamingText.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.streamingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is AtsSuggestionState.Ready -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScoreGauge(score = state.matchPercentage, size = 72.dp)
                    Text(
                        stringResource(R.string.apply_ats_score, state.matchPercentage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (state.matchedKeywords.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.apply_ats_matched), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(state.matchedKeywords.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                }
                if (state.missingKeywords.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.apply_ats_missing), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(state.missingKeywords.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            is AtsSuggestionState.Failed -> {
                FailedRow(messageFor(state.message))
                Spacer(Modifier.height(12.dp))
                AivanceSecondaryButton(text = stringResource(R.string.apply_ats_run), onClick = onRun, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CoverLetterSection(
    state: CoverLetterSuggestionState,
    onGenerate: () -> Unit,
    onCopy: () -> Unit
) {
    AssistCard {
        SectionTitle(Icons.Rounded.HistoryEdu, stringResource(R.string.apply_section_cover))
        Spacer(Modifier.height(12.dp))
        when (state) {
            CoverLetterSuggestionState.Idle -> {
                AivancePrimaryButton(
                    text = stringResource(R.string.apply_cover_generate),
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.AutoAwesome
                )
            }
            is CoverLetterSuggestionState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.apply_cover_generating), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.streamingText.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.streamingText, style = MaterialTheme.typography.bodySmall)
                }
            }
            is CoverLetterSuggestionState.Ready -> {
                SelectionContainer {
                    Text(state.text, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                AivanceSecondaryButton(
                    text = stringResource(R.string.apply_cover_copy),
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.ContentCopy
                )
            }
            is CoverLetterSuggestionState.Failed -> {
                FailedRow(messageFor(state.message))
                Spacer(Modifier.height(12.dp))
                AivanceSecondaryButton(text = stringResource(R.string.apply_cover_generate), onClick = onGenerate, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RecruiterSection(
    state: RecruiterSuggestionState,
    onFind: () -> Unit,
    onCopyEmail: (String) -> Unit
) {
    AssistCard {
        SectionTitle(Icons.Rounded.PersonSearch, stringResource(R.string.apply_section_recruiters))
        Spacer(Modifier.height(12.dp))
        when (state) {
            RecruiterSuggestionState.Idle -> {
                AivancePrimaryButton(
                    text = stringResource(R.string.apply_recruiters_find),
                    onClick = onFind,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Email
                )
            }
            RecruiterSuggestionState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.apply_recruiters_finding), style = MaterialTheme.typography.bodyMedium)
                }
            }
            is RecruiterSuggestionState.Ready -> {
                val withEmails = state.recruiters.filter { it.contacts.isNotEmpty() }
                if (withEmails.isEmpty()) {
                    Text(stringResource(R.string.apply_recruiters_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        withEmails.forEach { recruiter ->
                            RecruiterContactRow(recruiter, onCopyEmail)
                        }
                    }
                }
            }
            is RecruiterSuggestionState.Failed -> {
                FailedRow(messageFor(state.message))
                Spacer(Modifier.height(12.dp))
                AivanceSecondaryButton(text = stringResource(R.string.apply_recruiters_find), onClick = onFind, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RecruiterContactRow(recruiter: Recruiter, onCopyEmail: (String) -> Unit) {
    val contact = recruiter.contacts.maxByOrNull { it.confidence } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(recruiter.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                if (contact.isVerified) {
                    Icon(Icons.Rounded.Verified, stringResource(R.string.apply_verified), tint = AivanceTheme.colors.info, modifier = Modifier.size(14.dp))
                }
            }
            Text(contact.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                if (contact.isVerified) stringResource(R.string.apply_verified)
                else stringResource(R.string.apply_confidence, contact.confidence),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onCopyEmail(contact.email) }) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.apply_recruiters_copy_email))
        }
    }
}

@Composable
private fun FailedRow(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/**
 * Maps the ViewModel's stable sentinel codes to localized copy, falling back to
 * the raw engine message for genuine failures.
 */
@Composable
private fun messageFor(raw: String): String = when (raw) {
    "no_resume" -> stringResource(R.string.apply_no_resume)
    "no_domain" -> stringResource(R.string.apply_no_company_domain)
    else -> raw
}

private fun openExternally(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
