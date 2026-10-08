package com.example.wifidrop

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

internal enum class QetaraAppearance(val titleRes: Int) {
    SYSTEM(R.string.shell_automatic),
    IVORY(R.string.shell_ivory),
    BLUE_GRAY(R.string.shell_blue_gray),
    DARK(R.string.shell_dark);

    val title: String get() = appString(titleRes)

    fun resolved(systemDark: Boolean): QetaraAppearance = when (this) {
        SYSTEM -> if (systemDark) DARK else BLUE_GRAY
        else -> this
    }

    companion object {
        fun fromStored(raw: String?): QetaraAppearance =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

private const val APPEARANCE_PREFS_NAME = "qetara_appearance"
private const val APPEARANCE_KEY = "appearance"

private fun appearancePreferences(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(APPEARANCE_PREFS_NAME, Context.MODE_PRIVATE)

private fun readAppearance(preferences: SharedPreferences): QetaraAppearance {
    val stored = try {
        preferences.getString(APPEARANCE_KEY, null)
    } catch (_: ClassCastException) {
        null
    }
    return QetaraAppearance.fromStored(stored)
}

internal object QetaraAppearanceStore {
    fun load(context: Context): QetaraAppearance = readAppearance(appearancePreferences(context))

    fun save(context: Context, appearance: QetaraAppearance) {
        appearancePreferences(context).edit { putString(APPEARANCE_KEY, appearance.name) }
    }
}

@Composable
internal fun rememberQetaraAppearance(): State<QetaraAppearance> {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { appearancePreferences(context) }
    val appearance = remember(preferences) { mutableStateOf(readAppearance(preferences)) }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPreferences, key ->
            if (key == APPEARANCE_KEY || key == null) {
                appearance.value = readAppearance(changedPreferences)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        // Reconcile any write between the initial read and listener registration.
        appearance.value = readAppearance(preferences)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return appearance
}
