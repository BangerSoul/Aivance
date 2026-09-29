package com.bangersoul.aivance.core.designsystem.shell

import androidx.compose.runtime.compositionLocalOf
import com.bangersoul.aivance.core.common.model.AssistantJobContext

/**
 * Values that the app shell provides to its content — used to show
 * global snackbars, dialogs, loading overlays, or to summon the global
 * AI Assistant overlay (optionally with a job context attached).
 */
data class AppShellState(
    val showSnackbar: (String) -> Unit = {},
    val showDialog: (String, String, String) -> Unit = { _, _, _ -> },
    val dismissDialog: () -> Unit = {},
    val toggleAssistant: (Boolean) -> Unit = {},
    /** Attaches a job context (or clears it) before the assistant overlay opens. */
    val setAssistantJobContext: (AssistantJobContext?) -> Unit = {}
)

val LocalAppShellState = compositionLocalOf { AppShellState() }
