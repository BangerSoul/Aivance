package com.bangersoul.aivance.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangersoul.aivance.feature.profile.R
import com.bangersoul.aivance.core.designsystem.theme.DarkAccent
import com.bangersoul.aivance.feature.profile.AuthUiEvent
import com.bangersoul.aivance.feature.profile.AuthUiState
import com.bangersoul.aivance.feature.profile.AuthViewModel

/**
 * v2 authentication screen with two modes — Sign In and Create Account.
 *
 * Routing contract (consumed by [AivanceNavGraph]):
 *  - [AuthUiState.NewUser]      → ProviderSetup (first launch)
 *  - [AuthUiState.ReturningUser] → Dashboard
 *
 * This is the single pre-auth surface (AUDIT 6): the standalone Welcome screen
 * was deleted and its brand mark folded in here, so the product introduces
 * itself on the same screen that signs the user in — instead of a marketing
 * page whose "Get Started" and "Skip for now" both led to exactly this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNewUser: () -> Unit = {},
    onReturningUser: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isCreateMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.NewUser -> {
                viewModel.consumeResult()
                onNewUser()
            }
            is AuthUiState.ReturningUser -> {
                viewModel.consumeResult()
                onReturningUser()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            // Lift the scroll viewport above the keyboard so the Continue
            // button stays reachable while typing the email (edge-to-edge +
            // adjustResize delivers IME insets; without imePadding the last
            // form controls hide behind the keyboard).
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brand mark
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = DarkAccent.copy(alpha = 0.16f),
            modifier = Modifier.size(72.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = DarkAccent
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.welcome_brand),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = DarkAccent
        )
        Spacer(Modifier.height(24.dp))
        Text(
            if (isCreateMode) {
                stringResource(R.string.auth_create_account_title)
            } else {
                stringResource(R.string.auth_welcome_back_title)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isCreateMode) {
                stringResource(R.string.auth_create_subtitle)
            } else {
                stringResource(R.string.auth_signin_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.85f)
        )
        Spacer(Modifier.height(28.dp))

        // Mode toggle
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !isCreateMode,
                onClick = { isCreateMode = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(stringResource(R.string.auth_sign_in))
            }
            SegmentedButton(
                selected = isCreateMode,
                onClick = { isCreateMode = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(stringResource(R.string.auth_create_account))
            }
        }

        Spacer(Modifier.height(24.dp))

        if (isCreateMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(R.string.auth_first_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(R.string.auth_last_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.auth_email)) },
            placeholder = { Text(stringResource(R.string.auth_email_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (isCreateMode) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.auth_phone_optional)) },
                placeholder = { Text(stringResource(R.string.auth_phone_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                viewModel.onEvent(
                    AuthUiEvent.ContinueWithEmail(
                        email = email,
                        firstName = firstName,
                        lastName = lastName,
                        phone = phone
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            enabled = email.isNotBlank() && uiState !is AuthUiState.Loading,
            shape = MaterialTheme.shapes.medium
        ) {
            if (uiState is AuthUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.auth_continue), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.auth_or_separator),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        val context = LocalContext.current
        OutlinedButton(
            onClick = { viewModel.onEvent(AuthUiEvent.ContinueWithGoogle(context)) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Rounded.GTranslate, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.auth_google), fontWeight = FontWeight.Medium)
        }

        (uiState as? AuthUiState.Error)?.let { error ->
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

    }
}
