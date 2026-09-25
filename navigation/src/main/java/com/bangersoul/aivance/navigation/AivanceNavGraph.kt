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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import com.bangersoul.aivance.core.common.model.AssistantJobContext
import com.bangersoul.aivance.core.designsystem.shell.LocalAppShellState
import com.bangersoul.aivance.feature.analytics.AnalyticsScreen
import com.bangersoul.aivance.feature.analytics.AnalyticsViewModel
import com.bangersoul.aivance.feature.assistant.AssistantScreen
import com.bangersoul.aivance.feature.assistant.AssistantViewModel
import com.bangersoul.aivance.feature.ats.AtsScreen
import com.bangersoul.aivance.feature.coverletter.CoverLetterScreen
import com.bangersoul.aivance.feature.dashboard.DashboardScreen
import com.bangersoul.aivance.feature.interview.InterviewViewModel
import com.bangersoul.aivance.feature.interview.ui.PrepStudioScreen
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
import com.bangersoul.aivance.feature.resume.IntelligenceHubScreen
import com.bangersoul.aivance.feature.resume.IntelligenceHubViewModel
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
 * Architecture:
 *  - One independent backstack per primary workspace (Dashboard, Intelligence,
 *    Discovery, Pipeline, Prep Studio) so context switching never loses state.
 *  - A single gate backstack owns the unauthenticated surface: splash, welcome,
 *    auth and provider setup (the only onboarding flow).
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

    val backstacks = Destination.rootDestinations.associateWith { root ->
        @Suppress("UNCHECKED_CAST")
        rememberNavBackStack(root) as NavBackStack<Destination>
    }

    val authBackstack = rememberNavBackStack(
        if (initialDestination in Destination.authDestinations) initialDestination else Destination.Splash
    ) as NavBackStack<Destination>

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

    // Workspace backstacks exist only while the provider contract allows product
    // entry. A signed-in user whose configuration became invalid is held on the
    // auth backstack (provider setup) even though `isAuthed` is true.
    val currentBackstack = if (isAuthed && productEntryAllowed) {
        backstacks[activeWorkspace] ?: backstacks[Destination.Dashboard]!!
    } else {
        authBackstack
    }

    val currentDestination = currentBackstack.last()

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

            // Tab destinations switch workspaces instead of pushing.
            destination in Destination.rootDestinations ->
                activeWorkspace = destination

            destination in Destination.authDestinations ->
                authBackstack.add(destination)

            else -> {
                // Detail destinations belong to the workspace that owns them;
                // switching happens before the push so back returns to the
                // origin workspace's prior screen, never a foreign tab.
                val targetWorkspace = when {
                    destination is Destination.Ats ||
                        destination is Destination.ResumeDetail ||
                        destination is Destination.ResumeEngine ||
                        destination == Destination.Intelligence -> Destination.Intelligence

                    destination is Destination.CoverLetter ||
                        destination is Destination.DiscoverBySkill ||
                        destination is Destination.ApplyBrowser ||
                        destination is Destination.RecruiterDashboard -> Destination.Discovery

                    destination is Destination.LearnSkill -> Destination.PrepStudio

                    destination is Destination.TrackApplication -> Destination.Pipeline

                    else -> null
                }

                if (targetWorkspace != null && activeWorkspace != targetWorkspace) {
                    activeWorkspace = targetWorkspace
                }

                val targetBackstack = if (isAuthed) {
                    backstacks[targetWorkspace ?: activeWorkspace] ?: currentBackstack
                } else {
                    authBackstack
                }
                targetBackstack.add(destination)
            }
        }
    }

    LaunchedEffect(initialDestination) {
        if (initialDestination in Destination.rootDestinations) {
            activeWorkspace = initialDestination
        }
    }

    // ── System back ─────────────────────────────────────────────────────────

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
                Destination.rootDestinations.forEach { workspace ->
                    item(
                        selected = activeWorkspace == workspace,
                        onClick = { activeWorkspace = workspace },
                        icon = {
                            val selected = activeWorkspace == workspace
                            workspace.icon?.let {
                                Icon(
                                    it,
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
            NavHostContent(currentBackstack, onNavigate, authViewModel)
        }
    } else {
        NavHostContent(currentBackstack, onNavigate, authViewModel)
    }
}

@Composable
private fun NavHostContent(
    backStack: NavBackStack<Destination>,
    onNavigate: (Destination) -> Unit,
    authViewModel: AuthenticationViewModel
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
            ScreenContent(destination, onNavigate, authViewModel) {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            }
        }
    }
}

@Composable
private fun ScreenContent(
    destination: Destination?,
    onNavigate: (Destination) -> Unit,
    authViewModel: AuthenticationViewModel,
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
            onGetStarted = { onNavigate(Destination.Auth) },
            // The welcome "skip" makes no product promise — it goes to auth, and
            // the provider gate decides between the main graph and provider setup.
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
            onNavigateToResume = { onNavigate(Destination.Intelligence) },
            onNavigateToTracker = { onNavigate(Destination.Pipeline) },
            onNavigateToProfile = { onNavigate(Destination.IdentityHub) },
            onNavigateToInterview = { onNavigate(Destination.PrepStudio) },
            onNavigateToAnalytics = { onNavigate(Destination.Analytics) },
            onNavigateToJobs = { onNavigate(Destination.Discovery) },
            onNavigateToAssistant = { onNavigate(Destination.Assistant) },
            onNavigateToNotifications = { onNavigate(Destination.Notifications) },
            onNavigateToProviderSetup = { onNavigate(Destination.ProviderSetup) },
            onDiscoverBySkill = { skill -> onNavigate(Destination.DiscoverBySkill(skill)) },
            onLearnSkill = { skill -> onNavigate(Destination.LearnSkill(skill)) }
        )

        Destination.Assistant -> AssistantScreen(
            viewModel = hiltViewModel<AssistantViewModel>(),
            onSwitchProvider = { onNavigate(Destination.ProviderSetup) }
        )

        Destination.Intelligence -> IntelligenceHubScreen(
            viewModel = hiltViewModel<IntelligenceHubViewModel>(),
            onNavigateToEngine = { onNavigate(Destination.ResumeEngine()) },
            onNavigateToAts = { reportId -> onNavigate(Destination.Ats(reportId = reportId)) },
            onBack = onBack
        )

        is Destination.ResumeEngine -> ResumeEngineScreen(
            viewModel = hiltViewModel<ResumeEngineViewModel>(),
            initialJobDescription = destination.jobDescription,
            onBack = onBack
        )

        Destination.Discovery -> JobsScreen(
            viewModel = hiltViewModel(),
            onNavigateToDetails = { onNavigate(Destination.JobDetails(it)) },
            onNavigateToSavedJobs = { onNavigate(Destination.SavedJobs) }
        )

        is Destination.DiscoverBySkill -> JobsScreen(
            viewModel = hiltViewModel(),
            initialQuery = destination.skill,
            onNavigateToDetails = { onNavigate(Destination.JobDetails(it)) },
            onNavigateToSavedJobs = { onNavigate(Destination.SavedJobs) }
        )

        Destination.Pipeline -> TrackerScreen(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onNavigateToAnalytics = { onNavigate(Destination.Analytics) }
        )

        is Destination.TrackApplication -> TrackerScreen(
            viewModel = hiltViewModel(),
            initialJobId = destination.jobId,
            onBack = onBack,
            onNavigateToAnalytics = { onNavigate(Destination.Analytics) }
        )

        Destination.IdentityHub -> IdentityHubScreen(
            viewModel = hiltViewModel(),
            onBack = onBack,
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
            onNavigateToIntelligence = { onNavigate(Destination.Intelligence) }
        )

        Destination.PrepStudio -> PrepStudioScreen(
            interviewViewModel = hiltViewModel<InterviewViewModel>(),
            onBack = onBack
        )

        is Destination.LearnSkill -> PrepStudioScreen(
            interviewViewModel = hiltViewModel<InterviewViewModel>(),
            initialLearnSkill = destination.skill,
            onBack = onBack
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
            onFindJobs = { onNavigate(Destination.Discovery) }
        )

        Destination.SavedJobs -> SavedJobsScreen(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onJobClick = { onNavigate(Destination.JobDetails(it)) },
            onCreateResume = { job ->
                onNavigate(Destination.ResumeEngine(jobDescription = job.description))
            },
            onTrackApplication = { job -> onNavigate(Destination.TrackApplication(job.id)) },
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
            onNavigateToPipeline = { onNavigate(Destination.Pipeline) },
            onNavigateToAts = { description -> onNavigate(Destination.Ats(jobDescription = description)) },
            onNavigateToCompany = { companyName -> onNavigate(Destination.CompanyDetail(companyName)) },
            onNavigateToPrepStudio = { onNavigate(Destination.PrepStudio) }
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
