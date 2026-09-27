package com.bangersoul.aivance.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bangersoul.aivance.feature.interview.InterviewViewModel
import com.bangersoul.aivance.feature.interview.ui.PrepStudioScreen
import com.bangersoul.aivance.feature.resume.IntelligenceHubScreen
import com.bangersoul.aivance.feature.resume.IntelligenceHubViewModel

/**
 * Sub-tabs of the N1 Studio workspace — the merged Intelligence + Prep Studio
 * surfaces. Segments are seeded by legacy deep-link destinations
 * ([Destination.Intelligence] → resumes, [Destination.LearnSkill] → practice).
 */
enum class StudioSegment(@StringRes val labelRes: Int) {
    RESUMES(R.string.studio_segment_resumes),
    PRACTICE(R.string.studio_segment_practice);

    companion object {
        /** Segment implied by a legacy Studio entry point. */
        fun from(destination: Destination): StudioSegment = when (destination) {
            Destination.PrepStudio, is Destination.LearnSkill -> PRACTICE
            else -> RESUMES
        }
    }
}

/**
 * N1 Studio workspace — hosts the merged resume-engineering and practice
 * surfaces behind segmented sub-tabs. Both ViewModels are scoped to the
 * workspace composable (not the segments), so switching between Resumes and
 * Practice never loses state.
 */
@Composable
fun StudioWorkspaceScreen(
    initialSegment: StudioSegment,
    onNavigateToEngine: () -> Unit,
    onNavigateToAts: (Long?) -> Unit,
    onBack: () -> Unit,
    initialLearnSkill: String? = null
) {
    val intelligenceViewModel: IntelligenceHubViewModel = hiltViewModel()
    val interviewViewModel: InterviewViewModel = hiltViewModel()

    // Segment survives process death via saveable; the initial value is seeded
    // by the entry destination (Studio defaults to Resumes).
    var segment by rememberSaveable { mutableStateOf(initialSegment) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = segment.ordinal,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            StudioSegment.entries.forEach { candidate ->
                Tab(
                    selected = segment == candidate,
                    onClick = { segment = candidate },
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
                    initialLearnSkill = initialLearnSkill.takeIf { current == StudioSegment.PRACTICE },
                    onBack = null
                )
            }
        }
    }
}
