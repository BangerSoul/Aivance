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
import com.bangersoul.aivance.feature.analytics.AnalyticsScreen
import com.bangersoul.aivance.feature.assistant.AssistantScreen
import com.bangersoul.aivance.feature.assistant.AssistantViewModel
import com.bangersoul.aivance.feature.ats.AtsScreen
import com.bangersoul.aivance.feature.coverletter.CoverLetterScreen
import com.bangersoul.aivance.feature.dashboard.DashboardScreen
import com.bangersoul.aivance.feature.interview.InterviewViewModel
import com.bangersoul.aivance.feature.interview.ui.PrepStudioScreen
import com.bangersoul.aivance.feature.jobs.CompanyDetailScreen
import com.bangersoul.aivance.feature.jobs.CompanyDetailViewModel
import com.bangersoul.aivance.feature.jobs.JobComparisonScreen
import com.bangersoul.aivance.feature.jobs.JobDetailsScreen
import com.bangersoul.aivance.feature.jobs.JobsScreen
import com.bangersoul.aivance.feature.jobs.SavedJobsScreen
import com.bangersoul.aivance.core.common.model.AssistantJobContext
import com.bangersoul.aivance.core.designsystem.shell.LocalAppShellState
import com.bangersoul.aivance.feature.profile.*
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
 * Top-level navigation for AiVance v2 — Workflow-Driven.
 *
 * Each primary hub (Dashboard, Intelligence, Discovery, Pipeline, PrepStudio)
 * maintains its own independent backstack to ensure zero progress loss during
 * workspace context switching.
 */
@Composable
fun AivanceNavGraph() {
    val authViewModel: AuthenticationViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    // R2.2: the central provider gate — one authority for product entry. It reads
    // persisted DataStore state (the deliberate provider-optional choice, onboarding
    // completion, saved provider configurations) plus the live provider statuses, so
    // neither a UI step nor un-persisted runtime state can grant entry.
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

    // R2.2: product entry requires the provider contract. UNCONFIGURED/INVALID
    // must be remediated in provider setup; OPTIONAL (explicit
    // "Continue without AI providers") and CONFIGURED may enter. Evaluated from
    // persisted state by AuthenticationViewModel.evaluateProviderGate — never from
    // an onboarding UI flag. A null gate (first evaluation in flight) defers:
    // the session keeps whatever access it had until the gate settles, so the
    // async cold-start hydration can never flash a false lock-out.
    val productEntryAllowed = when (providerGate) {
        ProviderGateState.OPTIONAL, ProviderGateState.CONFIGURED -> true
        ProviderGateState.UNCONFIGURED, ProviderGateState.INVALID -> false
        null -> true
    }

    // ── Workspace State Management ──────────────────────────────────────────

    // We maintain independent backstacks for each primary workspace hub.
    val backstacks = Destination.rootDestinations.associateWith { root ->
        @Suppress("UNCHECKED_CAST")
        rememberNavBackStack(root) as NavBackStack<Destination>
    }

    // A separate backstack for the non-authenticated/onboarding flow.
    @Suppress("UNCHECKED_CAST")
    val authBackstack = rememberNavBackStack(
        if (initialDestination in Destination.authDestinations) initialDestination else Destination.Splash
    ) as NavBackStack<Destination>

    // R2.2: when a settled gate denies entry while the user is signed in
    // (provider removed/invalidated mid-session, or the persisted contract
    // changed), the auth backstack is moved to provider setup for remediation.
    // Never fires while the gate is unset (null) — a cold-start evaluation in
    // flight must not yank the user.
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

    // The current active root workspace. Defaults to Dashboard.
    var activeWorkspace by remember { mutableStateOf<Destination>(Destination.Dashboard) }

    // R2.2: workspace backstacks exist only while the provider contract allows
    // product entry. A signed-in user whose configuration became invalid is held
    // on the auth backstack (provider setup) even though `isAuthed` is true.
    val currentBackstack = if (isAuthed && productEntryAllowed) {
        backstacks[activeWorkspace] ?: backstacks[Destination.Dashboard]!!
    } else {
        authBackstack
    }

    val currentDestination = currentBackstack.last()

    // ── Navigation Logic ──────────────────────────────────────────────────

    val onNavigate: (Destination) -> Unit = remember(currentBackstack, isAuthed, backstacks, authBackstack, productEntryAllowed) {
        { destination ->
            if (destination.isAuthenticatedDestination() && !isAuthed) {
                authBackstack.add(Destination.Auth)
            } else if (destination.isAuthenticatedDestination() && !productEntryAllowed) {
                // R2.2: authenticated but the provider contract is unsatisfied —
                // remediation goes through provider setup, not the main graph.
                authBackstack.add(Destination.ProviderSetup)
            } else if (destination in Destination.rootDestinations) {
                activeWorkspace = destination
            } else if (destination in Destination.authDestinations) {
                authBackstack.add(destination)
            } else {
                // ── Workflow-Aware Hub Switching ──────────────────────────────

                val targetWorkspace = when {
                    destination == Destination.Discovery -> Destination.Discovery

                    destination is Destination.Ats ||
                    destination is Destination.ResumeDetail ||
                    destination == Destination.Intelligence ||
                    destination is Destination.ResumeEngine -> Destination.Intelligence

                    destination is Destination.CoverLetter ||
                    destination == Destination.JobComparison ||
                    destination is Destination.DiscoverBySkill ||
                    destination is Destination.RecruiterDashboard -> Destination.Discovery

                    destination == Destination.PrepStudio ||
                    destination is Destination.LearnSkill -> Destination.PrepStudio
                    destination == Destination.Pipeline ||
                    destination is Destination.TrackApplication -> Destination.Pipeline
                    else -> null
                }

                if (targetWorkspace != null && activeWorkspace != targetWorkspace) {
                    activeWorkspace = targetWorkspace
                }

                val updatedBackstack = if (isAuthed) {
                    backstacks[targetWorkspace ?: activeWorkspace] ?: currentBackstack
                } else {
                    authBackstack
                }
                updatedBackstack.add(destination)
            }
        }
    }

    LaunchedEffect(initialDestination) {
        if (initialDestination in Destination.rootDestinations) {
            activeWorkspace = initialDestination
        }
    }

    // ── System Back Logic ────────────────────────────────────────────────

    val isAuthSurface = !isAuthed || currentDestination in Destination.authDestinations
    BackHandler(enabled = currentBackstack.size > 1 || (!isAuthSurface && activeWorkspace != Destination.Dashboard)) {
        if (currentBackstack.size > 1) {
            currentBackstack.removeAt(currentBackstack.lastIndex)
        } else if (!isAuthSurface && activeWorkspace != Destination.Dashboard) {
            activeWorkspace = Destination.Dashboard
        }
    }

    // ── Adaptive UI Shell ────────────────────────────────────────────────

    if (isAuthed && productEntryAllowed && !isAuthSurface) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                Destination.rootDestinations.forEach { workspace ->
                    item(
                        selected = activeWorkspace == workspace,
                        onClick = { activeWorkspace = workspace },
                        icon = {
                            val tint = if (activeWorkspace == workspace)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                            workspace.icon?.let { Icon(it, null, tint = tint) }
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
            (slideInHorizontally { it / 4 } + fadeIn()).togetherWith(slideOutHorizontally { -it / 4 } + fadeOut())
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
    when (destination) {        Destination.Splash -> {
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
                        // R2.2: resolve the provider gate on settled data before the
                        // cold-start product-entry decision.
                        when (authViewModel.resolveProviderGate()) {
                            ProviderGateState.CONFIGURED, ProviderGateState.OPTIONAL ->
                                onNavigate(Destination.Dashboard)
                            // Remediation: sent back to provider setup, not into a
                            // silently degraded product.
                            else -> onNavigate(Destination.ProviderSetup)
                        }
                    }
                }
            })
        }
        Destination.Welcome -> WelcomeScreen(
            onGetStarted = { onNavigate(Destination.Auth) },
            // R2.2: the welcome "skip" no longer promises product entry. It goes to
            // auth — after signing in, the provider gate decides between the main
            // graph and provider setup.
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

        Destination.Onboarding, Destination.ProviderSetup -> OnboardingScreen(
            viewModel = hiltViewModel(),
            onComplete = {
                // R2.2: completion re-runs the central gate on the freshly
                // persisted choice (validated provider saved, or the explicit
                // provider-optional opt-in). Navigation follows the gate —
                // never the step flow's own flag.
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
            viewModel = hiltViewModel(),
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
            // Firebase, flips auth state to Unauthenticated — which switches the
            // nav graph back to the auth backstack automatically.
            onSignedOut = { authViewModel.onEvent(AuthenticationUiEvent.Logout) }
        )
        Destination.About -> AboutScreen(
            onBack = onBack,
            onNavigateToResources = { onNavigate(Destination.Resources) }
        )
        Destination.Resources -> RemoteResourcesScreen(onBack = onBack)
        Destination.Analytics -> AnalyticsScreen(
            viewModel = hiltViewModel<com.bangersoul.aivance.feature.analytics.AnalyticsViewModel>(),
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
        Destination.JobComparison -> JobComparisonScreen(
            jobs = emptyList(), // In a real app, this would come from a WorkspaceManager or shared VM
            onBack = onBack
        )

        is Destination.JobDetails -> JobDetailsScreen(
            viewModel = hiltViewModel(),
            jobId = destination.jobId,
            onNavigateBack = onBack,
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
