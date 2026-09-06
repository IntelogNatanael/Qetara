package com.example.wifidrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.wifidrop.presentation.normalizeSessionPin

internal data class P2pPinSessionKey(
    val token: String,
    val expiresAtMs: Long,
    val mode: ConnectionMode,
    val targetIp: String?,
    val enabled: Boolean,
    val expired: Boolean,
    val syncing: Boolean
)

/** Ephemeral display state: never saved with the session, PIN or activity state. */
internal data class P2pPinVisibilityState(
    val session: P2pPinSessionKey,
    val observedPin: String,
    val revealed: Boolean = false
) {
    fun forSession(currentSession: P2pPinSessionKey, currentPin: String): P2pPinVisibilityState =
        if (session == currentSession && observedPin == currentPin) this
        else P2pPinVisibilityState(currentSession, currentPin)

    fun edited(raw: String): P2pPinVisibilityState = copy(observedPin = normalizeSessionPin(raw))
    fun hidden(): P2pPinVisibilityState = copy(revealed = false)
    fun toggled(isResumed: Boolean): P2pPinVisibilityState =
        if (isResumed) copy(revealed = !revealed) else hidden()
}

@Composable
internal fun P2pSessionPinField(
    state: P2pScreenState,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionKey = P2pPinSessionKey(
        token = state.authToken,
        expiresAtMs = state.sessionExpiresAtMs,
        mode = state.activeConnectionMode,
        targetIp = if (state.activeConnectionMode == ConnectionMode.WIFI_DIRECT) state.directTargetIp else state.resolvedTargetIp,
        enabled = state.sessionEnabled,
        expired = state.sessionExpired,
        syncing = state.sessionSyncing
    )
    var visibility by remember { mutableStateOf(P2pPinVisibilityState(sessionKey, state.sessionPin)) }
    val currentVisibility = visibility.forSession(sessionKey, state.sessionPin)
    SideEffect {
        // Read the latest value so a lifecycle concealment cannot be overwritten by an old render.
        visibility = visibility.forSession(sessionKey, state.sessionPin)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) visibility = visibility.hidden()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(hasFocus, imeBottom) {
        if (hasFocus && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Include the floating label, rather than keeping only the cursor in the viewport.
            bringIntoViewRequester.bringIntoView()
        }
    }
    Box(modifier = modifier.bringIntoViewRequester(bringIntoViewRequester).padding(top = 8.dp)) {
        OutlinedTextField(
            value = state.sessionPin,
            onValueChange = { raw ->
                // A user's own edit preserves the reveal choice; a replaced PIN conceals itself.
                visibility = visibility.forSession(sessionKey, state.sessionPin).edited(raw)
                onValueChange(raw)
            },
            enabled = !state.sessionSyncing,
            label = { Text("PIN de 6 dígitos") },
            modifier = Modifier.fillMaxWidth().onFocusChanged { hasFocus = it.isFocused },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                autoCorrectEnabled = false
            ),
            visualTransformation = if (currentVisibility.revealed) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(
                    onClick = {
                        visibility = visibility.forSession(sessionKey, state.sessionPin).toggled(
                            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        )
                    },
                    enabled = !state.sessionSyncing,
                    modifier = Modifier.semantics {
                        contentDescription = if (currentVisibility.revealed) "Ocultar PIN" else "Mostrar PIN"
                        stateDescription = if (currentVisibility.revealed) "PIN visible" else "PIN oculto"
                    }
                ) {
                    Text(if (currentVisibility.revealed) "Ocultar" else "Mostrar")
                }
            }
        )
    }
}
