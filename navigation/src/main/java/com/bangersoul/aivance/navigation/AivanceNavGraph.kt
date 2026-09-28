package com.bangersoul.aivance.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import com.bangersoul.aivance.core.common.model.AssistantJobContext
import com.bangersoul.aivance.core.designsystem.icon.AiOrbIcon
import com.bangersoul.aivance.core.designsystem.icon.IconVariant
import com.bangersoul.aivance.core.designsystem.shell.LocalAppShellState
import com.bangersoul.aivance.feature.analytics.AnalyticsScreen
import com.bangersoul.aivance.feature.analytics.AnalyticsViewModel
import com.bangersoul.aivance.feature.assistant.AssistantScreen
import com.bangersoul.aivance.feature.assistant.AssistantViewModel
import com.bangersoul.aivance.feature.ats.AtsScreen
import com.bangersoul.aivance.feature.coverletter.CoverLetterScreen
import com.bangersoul.aivance.feature.dashboard.DashboardScreen
import com.bangersoul.aivance.feature.jobs.ApplyBrowserScreen
import com.bangersoul.aivance.feature.jobs.CompanyDetailScreen
import com.bangersoul.aivance.feature.jobs.CompanyDetailViewModel
import com.bangersoul.aivance.feature.jobs.JobDetailsScreen
import com.bangersoul.aivance.feature.jobs.JobsScreen
import com.bangersoul.aivance.feature.jobs.SavedJobsScreen
import com.bangersoul.aivance.feature.profile.AboutScreen
import com.bangersoul.aivance.feature.profile.AppearanceScreen
import com.bangersoul.aivance.feature.profile.AuthScreen
import com.bangersoul.aivance.feature.profile.AuthenticationUiEvent
import com.bangersoul.aivance.feature.profile.AuthenticationUiState
import com.bangersoul.aivance.feature.profile.AuthenticationViewModel
import com.bangersoul.aivance.feature.profile.IdentityHubScreen
import com.bangersoul.aivance.feature.profile.OnboardingScreen
import com.bangersoul.aivance.feature.profile.PrivacyCenterScreen
import com.bangersoul.aivance.feature.profile.PrivacyViewModel
import com.bangersoul.aivance.feature.profile.ProviderGateState
import com.bangersoul.aivance.feature.profile.RemoteResourcesScreen
import com.bangersoul.aivance.feature.profile.SplashScreen
import com.bangersoul.aivance.feature.profile.WelcomeScreen
import com.bangersoul.aivance.feature.recruiter.RecruiterDashboardScreen
import com.bangersoul.aivance.feature.recruiter.RecruiterViewModel
import com.bangersoul.aivance.feature.resume.ResumeDetailScreen
import com.bangersoul.aivance.feature.resume.ResumeDetailViewModel
import com.bangersoul.aivance.feature.resume.ResumeEngineScreen
import com.bangersoul.aivance.feature.resume.ResumeEngineViewModel
import com.bangersoul.aivance.feature.tracker.TrackerScreen
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Top-level navigation for AiVance v2 — workflow-driven.
 *
 * Architecture (post subtraction-first pruning, AUDIT §3.2):
 *  - One independent backstack per workspace — Dashboard, Discovery, Pipeline
 *    and Studio — plus a dedicated orb backstack for the Assistant, so context
 *    switching never loses state.
 *  - A single gate backstack owns the unauthenticated surface: splash, welcome,
 *    auth and provider setup (the only onboarding flow).
 *  - Seeded hand-offs (`Studio(PRACTICE)`, `Discovery(query)`,
 *    `Pipeline(jobId)`) are nav arguments on the workspace that owns them, not
 *    separate destinations: they land on the tab's own stack and never fork a
 *    duplicate.
 *  - The provider gate is the sole authority for product entry: an
 *    unconfigured/invalid contract routes to provider setup regardless of
 *    auth state.
 */
@Composable
fun AivanceNavGraph() {
    val authViewModel: AuthenticationViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    // The central provider gate — one authority for product entry. It reads
    // persisted DataStore state (the deliberate provider-optional choice,
    // onboarding completion, saved provider configurations) plus the live
    // provider statuses, so neither a UI step nor un-persisted runtime state
    // can grant entry.
    val providerGate by authViewModel.providerGate.collectAsStateWithLifecycle()
    val deepLinkDestination = remember { DeepLinkHandler.consumePending() }

    val initialDestination = remember {
        when {
            deepLinkDestination != null -> {
                if (deepLinkDestination.isAuthenticatedDestination() &&
                    authState !is AuthenticationUiState.Authenticated
                ) Destination.Splash else deepLinkDestination
            }
            authState is AuthenticationUiState.Authenticated -> Destination.Dashboard
            else -> Destination.Splash
        }
    }

    AivanceAppShell {
        AivanceWorkflowNavGraph(initialDestination, authViewModel, providerGate)
    }
}

/**
 * The workspace that owns [destination]'s backstack, or `null` when the
 * destination is a spoke, a gate surface or the Assistant orb.
 *
 * Seeded variants resolve to the canonical tab instance they belong to — that
 * is what keeps one backstack per tab while the seed still travels as a nav
 * argument.
 */
private fun workspaceOwnerOf(destination: Destination): Destination? = when (destination) {
    Destination.Dashboard -> Destination.Dashboard
    Destination.AssistantOrb -> Destination.AssistantOrb
    is Destination.Discovery -> Destination.Discovery()
    is Destination.Pipeline -> Destination.Pipeline()
    is Destination.Studio -> Destination.Studio()
    else -> null
}

@Composable
private fun AivanceWorkflowNavGraph(
    initialDestination: Destination,
    authViewModel: AuthenticationViewModel,
    providerGate: ProviderGateState?
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isAuthed = authState is AuthenticationUiState.Authenticated

    // Product entry requires the provider contract. UNCONFIGURED/INVALID must be
    // remediated in provider setup; OPTIONAL (explicit "Continue without AI
    // providers") and CONFIGURED may enter. Evaluated from persisted state by
    // AuthenticationViewModel.evaluateProviderGate — never from an onboarding UI
    // flag. A null gate (first evaluation in flight) defers: the session keeps
    // whatever access it had until the gate settles, so async cold-start
    // hydration can never flash a false lock-out.
    val productEntryAllowed = when (providerGate) {
        ProviderGateState.OPTIONAL, ProviderGateState.CONFIGURED -> true
        ProviderGateState.UNCONFIGURED, ProviderGateState.INVALID -> false
        null -> true
    }

    // ── Backstacks ──────────────────────────────────────────────────────────

    // One backstack per workspace destination. The four N1 tabs own their own;
    // Navigation 3 has no popUpTo/launchSingleTop, so "saving state" is simply
    // the tab's own retained history: switching tabs swaps stacks instead of
    // pushing, and a re-seed replaces the previous seed of that tab.
    val backstacks = Destination.rootDestinations.associateWith { root ->
        @Suppress("UNCHECKED_CAST")
        rememberNavBackStack(root) as NavBackStack<Destination>
    }

    // The AI orb owns a dedicated backstack so assistant conversations survive
    // workspace switches without polluting a workspace's history.
    val orbBackstack = rememberNavBackStack(Destination.AssistantOrb) as NavBackStack<Destination>

    val authBackstack = rememberNavBackStack(
        if (initialDestination in Destination.authDestinations) initialDestination else Destination.Splash
    ) as NavBackStack<Destination>

    // Hoisted Studio segment: seeded by the Studio nav argument, then owned by
    // the user's tab choice. Kept at graph level (saveable) so leaving the
    // workspace — or a process recreation — does not silently drop it back to
    // Resumes.
    var studioSegment by rememberSaveable { mutableStateOf(StudioSegment.RESUMES) }

    // Identity Hub sub-tab, hoisted for the same reason (B5): the hub is a spoke
    // on a workspace stack, so a local `remember` inside it reset to the
    // Identity tab after every System-spoke round trip.
    var identityTab by rememberSaveable { mutableStateOf(0) }

    // When a settled gate denies entry while the user is signed in (provider
    // removed/invalidated mid-session, or the persisted contract changed), the
    // auth backstack moves to provider setup for remediation. Never fires while
    // the gate is unset (null) — a cold-start evaluation in flight must not
    // yank the user.
    LaunchedEffect(providerGate, isAuthed) {
        if (isAuthed && (providerGate == ProviderGateState.UNCONFIGURED ||
                providerGate == ProviderGateState.INVALID)
        ) {
            if (authBackstack.lastOrNull() != Destination.ProviderSetup) {
                authBackstack.clear()
                authBackstack.add(Destination.ProviderSetup)
            }
        }
    }

    var activeWorkspace by remember { mutableStateOf<Destination>(Destination.Dashboard) }

    fun stackFor(workspace: Destination): NavBackStack<Destination> = when (workspace) {
        Destination.AssistantOrb -> orbBackstack
        is Destination.Discovery -> backstacks.getValue(Destination.Discovery())
        is Destination.Pipeline -> backstacks.getValue(Destination.Pipeline())
        is Destination.Studio -> backstacks.getValue(Destination.Studio())
        else -> backstacks.getValue(Destination.Dashboard)
    }

    // Workspace backstacks exist only while the provider contract allows product
    // entry. A signed-in user whose configuration became invalid is held on the
    // auth backstack (provider setup) even though `isAuthed` is true.
    val currentBackstack = if (isAuthed && productEntryAllowed) {
        stackFor(activeWorkspace)
    } else {
        authBackstack
    }

    // ── Navigation logic ────────────────────────────────────────────────────

    val onNavigate: (Destination) -> Unit = { destination ->
        when {
            // Every authenticated surface funnels through auth first.
            destination.isAuthenticatedDestination() && !isAuthed ->
                authBackstack.add(Destination.Auth)

            // Authenticated but the provider contract is unsatisfied —
            // remediation goes through provider setup, not the main graph.
            destination.isAuthenticatedDestination() && !productEntryAllowed ->
                authBackstack.add(Destination.ProviderSetup)

            destination in Destination.authDestinations ->
                authBackstack.add(destination)

            else -> {
                val owner = workspaceOwnerOf(destination)
                if (owner != null) {
                    // Tab switch: the workspace's own saved history is shown as
                    // it was left. A seeded variant additionally lands as the
                    // tab's top entry — replacing an earlier seed of the same
                    // tab (launch-single-top) so repeated hand-offs never grow
                    // the stack.
                    activeWorkspace = owner
                    if (destination is Destination.Studio) studioSegment = destination.segment
                    if (destination != owner) {
                        val stack = stackFor(owner)
                        if (stack.size > 1 && stack.last()::class == destination::class) {
                            stack.removeAt(stack.lastIndex)
                        }
                        stack.add(destination)
                    }
                } else {
                    // Spokes belong to the workspace that owns them; switching
                    // happens before the push so back returns to the origin
                    // workspace's prior screen, never a foreign tab.
                    val targetWorkspace = when (destination) {
                        is Destination.Ats,
                        is Destination.ResumeDetail,
                        is Destination.ResumeEngine -> Destination.Studio()

                        is Destination.CoverLetter,
                        is Destination.ApplyBrowser,
                        is Destination.RecruiterDashboard -> Destination.Discovery()

                        else -> null
                    }

                    if (targetWorkspace != null && activeWorkspace != targetWorkspace) {
                        activeWorkspace = targetWorkspace
                    }

                    val targetBackstack = if (isAuthed) {
                        stackFor(targetWorkspace ?: activeWorkspace)
                    } else {
                        authBackstack
                    }
                    targetBackstack.add(destination)
                }
            }
        }
    }

    // Deep links land on the workspace that owns the seeded entry: activate the
    // tab, publish the seed, and place the seeded entry on its stack so the
    // destination (not just the tab) is what the user actually sees.
    LaunchedEffect(initialDestination) {
        val owner = workspaceOwnerOf(initialDestination) ?: return@LaunchedEffect
        activeWorkspace = owner
        if (initialDestination is Destination.Studio) studioSegment = initialDestination.segment
        if (initialDestination != owner) {
            stackFor(owner).add(initialDestination)
        }
    }

    // ── System back ─────────────────────────────────────────────────────────

    val currentDestination = currentBackstack.last()
    val isAuthSurface = !isAuthed || currentDestination in Destination.authDestinations
    BackHandler(enabled = currentBackstack.size > 1 || (!isAuthSurface && activeWorkspace != Destination.Dashboard)) {
        if (currentBackstack.size > 1) {
            currentBackstack.removeAt(currentBackstack.lastIndex)
        } else if (!isAuthSurface && activeWorkspace != Destination.Dashboard) {
            activeWorkspace = Destination.Dashboard
        }
    }

    // ── Adaptive shell ──────────────────────────────────────────────────────

    if (isAuthed && productEntryAllowed && !isAuthSurface) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                // ── Aurora orb slot (N1) — the Assistant as a permanent nav
                // surface, rendered with the kit's gradient instead of a tab icon.
                item(
                    selected = activeWorkspace == Destination.AssistantOrb,
                    onClick = { activeWorkspace = Destination.AssistantOrb },
                    icon = { AiOrbIcon(size = 24.dp, contentDescription = null) },
                    label = { Text(stringResource(Destination.AssistantOrb.labelRes)) }
                )

                // ── Workspaces — duotone icons (I3): outlined when inactive,
                // filled when active.
                Destination.rootDestinations.forEach { workspace ->
                    item(
                        selected = activeWorkspace == workspace,
                        // Tabs swap stacks; they never re-seed. The Studio
                        // segment is the user's own choice once inside the
                        // workspace, so a tab tap must not reset it.
                        onClick = { activeWorkspace = workspace },
                        icon = {
                            val selected = activeWorkspace == workspace
                            val variant = if (selected) IconVariant.FILLED else IconVariant.OUTLINED
                            workspace.iconIntent.forVariant(variant)?.let { vector ->
                                Icon(
                                    vector,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        label = { Text(stringResource(workspace.labelRes)) }
                    )
                }
            }
        ) {
            NavHostContent(
                backStack = currentBackstack,
                onNavigate = onNavigate,
                authViewModel = authViewModel,
                studioSegment = studioSegment,
                onStudioSegmentChange = { studioSegment = it },
                identityTab = identityTab,
                onIdentityTabChange = { identityTab = it }
            )
        }
    } else {
        NavHostContent(
            backStack = currentBackstack,
            onNavigate = onNavigate,
            authViewModel = authViewModel,
            studioSegment = studioSegment,
            onStudioSegmentChange = { studioSegment = it },
            identityTab = identityTab,
            onIdentityTabChange = { identityTab = it }
        )
    }
}

@Composable
private fun NavHostContent(
    backStack: NavBackStack<Destination>,
    onNavigate: (Destination) -> Unit,
    authViewModel: AuthenticationViewModel,
    studioSegment: StudioSegment,
    onStudioSegmentChange: (StudioSegment) -> Unit,
    identityTab: Int,
    onIdentityTabChange: (Int) -> Unit
) {
    val currentDestination = if (backStack.isNotEmpty()) backStack.last() else return
    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            (slideInHorizontally { it / 4 } + fadeIn()).togetherWith(
                slideOutHorizontally { -it / 4 } + fadeOut()
            )
        },
        label = "NavTransition"
    ) { destination ->
        Box(Modifier.fillMaxSize()) {
            ScreenContent(
                destination = destination,
                onNavigate = onNavigate,
                authViewModel = authViewModel,
                studioSegment = studioSegment,
                onStudioSegmentChange = onStudioSegmentChange,
                identityTab = identityTab,
                onIdentityTabChange = onIdentityTabChange,
                onBack = {
                    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }
            )
        }
    }
}

@Composable
private fun ScreenContent(
    destination: Destination?,
    onNavigate: (Destination) -> Unit,
    authViewModel: AuthenticationViewModel,
    studioSegment: StudioSegment,
    onStudioSegmentChange: (StudioSegment) -> Unit,
    identityTab: Int,
    onIdentityTabChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    val shellState = LocalAppShellState.current
    when (destination) {
        Destination.Splash -> {
            val splashScope = rememberCoroutineScope()
            SplashScreen(onSplashComplete = {
                splashScope.launch {
                    val settled = withTimeoutOrNull(3_000L) {
                        authViewModel.uiState
                            .filter { it !is AuthenticationUiState.Loading }
                            .first()
                    }
                    val isAuthed =
                        (settled ?: authViewModel.uiState.value) is AuthenticationUiState.Authenticated
                    if (!isAuthed) {
                        onNavigate(Destination.Welcome)
                    } else {
                        // Resolve the provider gate on settled data before the
                        // cold-start product-entry decision. Remediation goes to
                        // provider setup, never into a silently degraded product.
                        when (authViewModel.resolveProviderGate()) {
                            ProviderGateState.CONFIGURED, ProviderGateState.OPTIONAL ->
                                onNavigate(Destination.Dashboard)
                            else -> onNavigate(Destination.ProviderSetup)
                        }
                    }
                }
            })
        }

        Destination.Welcome -> WelcomeScreen(
            // Both affordances make the same product promise — sign in. The
            // provider gate, not this screen, decides what happens next.
            onGetStarted = { onNavigate(Destination.Auth) },
            onSkip = { onNavigate(Destination.Auth) }
        )

        Destination.Auth -> AuthScreen(
            viewModel = hiltViewModel(),
            onNewUser = { onNavigate(Destination.ProviderSetup) },
            onReturningUser = {
                authViewModel.onEvent(AuthenticationUiEvent.CheckAuth)
            },
            onBackToWelcome = { onNavigate(Destination.Welcome) }
        )

        Destination.ProviderSetup -> OnboardingScreen(
            viewModel = hiltViewModel(),
            onComplete = {
                // Completion re-runs the central gate on the freshly persisted
                // choice (validated provider saved, or the explicit
                // provider-optional opt-in). Navigation follows the gate — never
                // the step flow's own flag.
                authViewModel.onEvent(AuthenticationUiEvent.CheckAuth)
            }
        )

        Destination.Dashboard -> DashboardScreen(
            viewModel = hiltViewModel(),
            onNavigateToResume = { onNavigate(Destination.Studio(segment = StudioSegment.RESUMES)) },
            onNavigateToTracker = { onNavigate(Destination.Pipeline()) },
            onNavigateToProfile = { onNavigate(Destination.IdentityHub) },
            onNavigateToInterview = { onNavigate(Destination.Studio(segment = StudioSegment.PRACTICE)) },
            onNavigateToAnalytics = { onNavigate(Destination.Analytics) },
            onNavigateToJobs = { onNavigate(Destination.Discovery()) },
            onNavigateToNotifications = { onNavigate(Destination.Notifications) },
            onNavigateToProviderSetup = { onNavigate(Destination.ProviderSetup) },
            onDiscoverBySkill = { skill -> onNavigate(Destination.Discovery(query = skill)) },
            onLearnSkill = { skill ->
                onNavigate(Destination.Studio(segment = StudioSegment.PRACTICE, learnSkill = skill))
            }
        )

        // The orb owns its own backstack — a conversation survives workspace
        // switches, and there is no second assistant surface to reconcile.
        Destination.AssistantOrb -> AssistantScreen(
            viewModel = hiltViewModel<AssistantViewModel>(),
            onSwitchProvider = { onNavigate(Destination.ProviderSetup) }
        )

        is Destination.Studio -> StudioWorkspaceScreen(
            segment = studioSegment,
            onSegmentChange = onStudioSegmentChange,
            initialLearnSkill = destination.learnSkill,
            onNavigateToEngine = { onNavigate(Destination.ResumeEngine()) },
            onNavigateToAts = { reportId -> onNavigate(Destination.Ats(reportId = reportId)) },
            onBack = onBack
        )

        is Destination.ResumeEngine -> ResumeEngineScreen(
            viewModel = hiltViewModel<ResumeEngineViewModel>(),
            initialJobDescription = destination.jobDescription,
            onBack = onBack
        )

        is Destination.Discovery -> JobsScreen(
            viewModel = hiltViewModel(),
            initialQuery = destination.query,
            onNavigateToDetails = { onNavigate(Destination.JobDetails(it)) },
            onNavigateToSavedJobs = { onNavigate(Destination.SavedJobs) },
            // Quick Match can only run with a target role; without one the chip
            // sends the user to the profile that defines it.
            onSetTargetRole = { onNavigate(Destination.IdentityHub) }
        )

        is Destination.Pipeline -> TrackerScreen(
            viewModel = hiltViewModel(),
            onBack = onBack,
            // The saved-job hand-off folds into the tab: the job id pre-selects
            // the card on the Pipeline's own stack instead of pushing a second
            // Tracker screen.
            initialJobId = destination.jobId,
            onNavigateToAnalytics = { onNavigate(Destination.Analytics) }
        )

        Destination.IdentityHub -> IdentityHubScreen(
            viewModel = hiltViewModel(),
            onBack = onBack,
            selectedTab = identityTab,
            onTabChange = onIdentityTabChange,
            onNavigateToAbout = { onNavigate(Destination.About) },
            onNavigateToResources = { onNavigate(Destination.Resources) },
            onNavigateToAppearance = { onNavigate(Destination.Appearance) },
            onNavigateToPrivacy = { onNavigate(Destination.PrivacyCenter) },
            onNavigateToProviderManagement = { onNavigate(Destination.ProviderManagement) },
            // Route through the auth ViewModel's full logout: it clears the
            // session, API key AND the onboarding-completed gate (otherwise the
            // next cold start would silently log the user back in), signs out of
            // Firebase, and flips auth state to Unauthenticated — which switches
            // the nav graph back to the auth backstack automatically.
            onSignedOut = { authViewModel.onEvent(AuthenticationUiEvent.Logout) }
        )

        Destination.About -> AboutScreen(
            onBack = onBack,
            onNavigateToResources = { onNavigate(Destination.Resources) }
        )

        Destination.Resources -> RemoteResourcesScreen(onBack = onBack)

        Destination.Analytics -> AnalyticsScreen(
            viewModel = hiltViewModel<AnalyticsViewModel>(),
            onBack = onBack,
            // The old Intelligence spoke is gone; career surfaces live in Studio.
            onNavigateToIntelligence = { onNavigate(Destination.Studio(segment = StudioSegment.RESUMES)) }
        )

        is Destination.Ats -> AtsScreen(
            viewModel = hiltViewModel(),
            onNavigateBack = onBack,
            initialJobDescription = destination.jobDescription,
            initialReportId = destination.reportId,
            onNavigateToCoverLetter = { onNavigate(Destination.CoverLetter(jobId = null)) }
        )

        is Destination.CoverLetter -> CoverLetterScreen(
            viewModel = hiltViewModel(),
            onNavigateBack = onBack,
            jobId = destination.jobId,
            onFindJobs = { onNavigate(Destination.Discovery()) }
        )

        Destination.SavedJobs -> SavedJobsScreen(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onJobClick = { onNavigate(Destination.JobDetails(it)) },
            onCreateResume = { job ->
                onNavigate(Destination.ResumeEngine(jobDescription = job.description))
            },
            // TrackApplication is gone: the job id now travels as the Pipeline
            // tab's nav argument, so the kanban opens on the job without a
            // duplicate Tracker entry on the stack.
            onTrackApplication = { job -> onNavigate(Destination.Pipeline(jobId = job.id)) },
            onAssistantForJob = { job ->
                shellState.setAssistantJobContext(
                    AssistantJobContext(
                        jobId = job.id,
                        title = job.title,
                        company = job.company,
                        description = job.description
                    )
                )
                shellState.toggleAssistant(true)
            }
        )

        is Destination.JobDetails -> JobDetailsScreen(
            viewModel = hiltViewModel(),
            jobId = destination.jobId,
            onNavigateBack = onBack,
            onNavigateToApplyBrowser = { onNavigate(Destination.ApplyBrowser(it)) },
            onNavigateToRecruiters = { onNavigate(Destination.RecruiterDashboard(it)) },
            onNavigateToCoverLetter = { jobId -> onNavigate(Destination.CoverLetter(jobId = jobId)) },
            onNavigateToPipeline = { onNavigate(Destination.Pipeline()) },
            onNavigateToAts = { description -> onNavigate(Destination.Ats(jobDescription = description)) },
            onNavigateToCompany = { companyName -> onNavigate(Destination.CompanyDetail(companyName)) },
            onNavigateToPrepStudio = { onNavigate(Destination.Studio(segment = StudioSegment.PRACTICE)) }
        )

        is Destination.RecruiterDashboard -> RecruiterDashboardScreen(
            viewModel = hiltViewModel<RecruiterViewModel>(),
            jobId = destination.jobId,
            onBack = onBack
        )

        is Destination.ApplyBrowser -> ApplyBrowserScreen(
            viewModel = hiltViewModel(),
            jobId = destination.jobId,
            onNavigateBack = onBack
        )

        is Destination.CompanyDetail -> CompanyDetailScreen(
            viewModel = hiltViewModel<CompanyDetailViewModel>(),
            companyId = destination.companyId,
            onBack = onBack,
            onNavigateToRecruiters = { onNavigate(Destination.RecruiterDashboard(it)) }
        )

        is Destination.ResumeDetail -> ResumeDetailScreen(
            viewModel = hiltViewModel<ResumeDetailViewModel>(),
            resumeId = destination.resumeId,
            onBack = onBack
        )

        Destination.Appearance -> AppearanceScreen(viewModel = hiltViewModel(), onBack = onBack)
        Destination.ProviderManagement -> ProviderManagementScreen(viewModel = hiltViewModel(), onBack = onBack)
        Destination.Notifications -> NotificationsScreen(viewModel = hiltViewModel(), onBack = onBack)
        Destination.PrivacyCenter -> PrivacyCenterScreen(
            viewModel = hiltViewModel<PrivacyViewModel>(),
            onBack = onBack
        )

        else -> InvalidRouteScreen(onBack)
    }
}
