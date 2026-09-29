package com.bangersoul.aivance.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.core.common.model.AssistantJobContext
import com.bangersoul.aivance.core.designsystem.shell.AppShellState
import com.bangersoul.aivance.core.designsystem.shell.LocalAppShellState
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import com.bangersoul.aivance.feature.assistant.AssistantScreen
import com.bangersoul.aivance.feature.assistant.AssistantViewModel
import kotlinx.coroutines.launch

/**
 * Global application shell wrapping the navigation graph.
 *
 * Responsibilities:
 * - Theme application (AivanceTheme)
 * - Global snackbar host
 * - Global dialog host
 * - Global AI Assistant Overlay (with optional job context)
 * - Scaffold edge-to-edge management
 */
@Composable
fun AivanceAppShell(
    content: @Composable () -> Unit
) {
    val themeViewModel: AppThemeViewModel = hiltViewModel()
    val themeState by themeViewModel.themeState.collectAsStateWithLifecycle()

    AivanceTheme(
        themeMode = themeState.themeMode,
        accentSeed = themeState.accentSeed,
        dynamicColor = themeState.dynamicColor,
        designKit = themeState.designKit
    ) {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var dialogState by remember { mutableStateOf<DialogState?>(null) }
        var isAssistantVisible by remember { mutableStateOf(false) }
        var assistantJobContext by remember { mutableStateOf<AssistantJobContext?>(null) }

        val shellState = remember {
            AppShellState(
                showSnackbar = { message ->
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                showDialog = { title, message, confirmLabel ->
                    dialogState = DialogState(
                        title = title,
                        message = message,
                        confirmLabel = confirmLabel
                    )
                },
                dismissDialog = { dialogState = null },
                toggleAssistant = { visible -> isAssistantVisible = visible },
                setAssistantJobContext = { context -> assistantJobContext = context }
            )
        }

        CompositionLocalProvider(LocalAppShellState provides shellState) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background
            ) {
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = com.bangersoul.aivance.core.designsystem.theme.DarkSurface,
                                contentColor = com.bangersoul.aivance.core.designsystem.theme.DarkPrimary
                            )
                        }
                    },
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        content()

                        // Global AI Assistant Overlay. Job context is scoped to a
                        // single open: it's cleared on dismiss so a stale job never
                        // leaks into prompts opened later from other screens.
                        if (isAssistantVisible) {
                            AssistantOverlay(
                                jobContext = assistantJobContext,
                                onDismiss = {
                                    isAssistantVisible = false
                                    assistantJobContext = null
                                }
                            )
                        }

                        // Global overlay dialog
                        dialogState?.let { state ->
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { dialogState = null },
                                title = { androidx.compose.material3.Text(state.title) },
                                text = { androidx.compose.material3.Text(state.message) },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(onClick = { dialogState = null }) {
                                        androidx.compose.material3.Text(state.confirmLabel)
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { dialogState = null }) {
                                        androidx.compose.material3.Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AssistantOverlay(
    jobContext: AssistantJobContext?,
    onDismiss: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f),
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets.ime }
    ) {
        Box(modifier = Modifier.fillMaxHeight(0.9f)) {
            AssistantScreen(
                viewModel = hiltViewModel<AssistantViewModel>(),
                initialJobContext = jobContext,
                onSwitchProvider = { /* Handled in screen */ }
            )
        }
    }
}

private data class DialogState(
    val title: String,
    val message: String,
    val confirmLabel: String
)
