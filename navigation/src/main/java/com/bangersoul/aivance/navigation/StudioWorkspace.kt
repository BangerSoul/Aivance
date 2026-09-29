package com.bangersoul.aivance.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bangersoul.aivance.feature.interview.InterviewViewModel
import com.bangersoul.aivance.feature.interview.ui.PrepStudioScreen
import com.bangersoul.aivance.feature.resume.IntelligenceHubScreen
import com.bangersoul.aivance.feature.resume.IntelligenceHubViewModel
import kotlinx.serialization.Serializable

/**
 * Sub-tabs of the N1 Studio workspace — the merged Intelligence + Prep Studio
 * surfaces. The active segment is hoisted to the nav graph (see
 * [AivanceNavGraph]) and seeded from the [Destination.Studio] nav argument, so
 * it survives workspace switches and process death. The legacy
 * Intelligence / PrepStudio / LearnSkill entry points that used to seed it are
 * gone.
 */
@Serializable
enum class StudioSegment(@StringRes val labelRes: Int) {
    RESUMES(R.string.studio_segment_resumes),
    PRACTICE(R.string.studio_segment_practice)
}

/**
 * N1 Studio workspace — hosts the merged resume-engineering and practice
 * surfaces behind segmented sub-tabs. Both ViewModels are scoped to the
 * workspace composable (not the segments), so switching between Resumes and
 * Practice never loses state.
 *
 * [segment] is the hoisted selection; [onSegmentChange] publishes the user's
 * choice back to the owner so it is restored when they leave and re-enter the
 * workspace.
 */
@Composable
fun StudioWorkspaceScreen(
    segment: StudioSegment,
    onSegmentChange: (StudioSegment) -> Unit,
    onNavigateToEngine: () -> Unit,
    onNavigateToAts: (Long?) -> Unit,
    onBack: () -> Unit,
    initialLearnSkill: String? = null
) {
    val intelligenceViewModel: IntelligenceHubViewModel = hiltViewModel()
    val interviewViewModel: InterviewViewModel = hiltViewModel()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = segment.ordinal,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            StudioSegment.entries.forEach { candidate ->
                Tab(
                    selected = segment == candidate,
                    onClick = { if (segment != candidate) onSegmentChange(candidate) },
                    text = { Text(stringResource(candidate.labelRes)) }
                )
            }
        }

        AnimatedContent(
            targetState = segment,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "StudioSegment",
            modifier = Modifier.fillMaxSize()
        ) { current ->
            when (current) {
                StudioSegment.RESUMES -> IntelligenceHubScreen(
                    viewModel = intelligenceViewModel,
                    onNavigateToEngine = onNavigateToEngine,
                    onNavigateToAts = onNavigateToAts,
                    // Workspace root: no back arrow — the nav bar owns egress.
                    onBack = null
                )

                StudioSegment.PRACTICE -> PrepStudioScreen(
                    interviewViewModel = interviewViewModel,
                    initialLearnSkill = initialLearnSkill,
                    onBack = null
                )
            }
        }
    }
}
