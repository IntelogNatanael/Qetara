package com.example.wifidrop

import android.content.Context
import androidx.core.content.edit

enum class FocusStage(private val titleResource: Int) {
    OFF(R.string.conn_full),
    CONNECT(R.string.conn_connect),
    SEND(R.string.conn_send),
    CHAT(R.string.conn_chat);

    val title: String get() = appString(titleResource)

    companion object {
        fun fromStored(raw: String?): FocusStage {
            return entries.firstOrNull { it.name == raw } ?: OFF
        }
    }
}

enum class ConnectionMode(private val titleResource: Int) {
    WIFI_DIRECT(R.string.conn_wifi_direct),
    LAN(R.string.conn_standard_wifi);

    val title: String get() = appString(titleResource)

    companion object {
        fun fromStored(raw: String?): ConnectionMode {
            return entries.firstOrNull { it.name == raw } ?: WIFI_DIRECT
        }
    }
}

enum class ConnectionViewMode(private val titleResource: Int) {
    WIFI_DIRECT(R.string.conn_wifi_direct),
    LAN(R.string.conn_wifi_lan),
    ADVANCED(R.string.conn_full);

    val title: String get() = appString(titleResource)

    companion object {
        fun fromStored(raw: String?): ConnectionViewMode {
            return entries.firstOrNull { it.name == raw } ?: WIFI_DIRECT
        }

        fun fromConnectionMode(mode: ConnectionMode): ConnectionViewMode {
            return when (mode) {
                ConnectionMode.WIFI_DIRECT -> WIFI_DIRECT
                ConnectionMode.LAN -> LAN
            }
        }
    }
}

data class UxPreferences(
    val fontScale: Float = 1.0f,
    val compactMode: Boolean = false,
    val vibrateOnConnect: Boolean = true,
    val vibrateOnError: Boolean = true,
    val silentSuccessFeedback: Boolean = false,
    val lastFocusStage: FocusStage = FocusStage.OFF,
    val activeConnectionMode: ConnectionMode = ConnectionMode.WIFI_DIRECT,
    val connectionViewMode: ConnectionViewMode = ConnectionViewMode.WIFI_DIRECT,
    val wifiDirectModeEnabled: Boolean = true,
    val lanModeEnabled: Boolean = true,
    val joinedGlobalLan: Boolean = false,
    val autoDownloadChannelFiles: Boolean = false,
    val sessionEnabled: Boolean = true
)

object UxPreferencesStore {
    private const val PREFS_NAME = "wifidrop_ux_preferences"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_COMPACT_MODE = "compact_mode"
    private const val KEY_VIBRATE_CONNECT = "vibrate_connect"
    private const val KEY_VIBRATE_ERROR = "vibrate_error"
    private const val KEY_SILENT_SUCCESS = "silent_success"
    private const val KEY_LAST_FOCUS_STAGE = "last_focus_stage"
    private const val KEY_ACTIVE_CONNECTION_MODE = "active_connection_mode"
    private const val KEY_CONNECTION_VIEW_MODE = "connection_view_mode"
    private const val KEY_WIFI_DIRECT_MODE_ENABLED = "wifi_direct_mode_enabled"
    private const val KEY_LAN_MODE_ENABLED = "lan_mode_enabled"
    private const val KEY_JOINED_GLOBAL_LAN = "joined_global_lan"
    private const val KEY_AUTO_DOWNLOAD_CHANNEL_FILES = "auto_download_channel_files"
    private const val KEY_SESSION_ENABLED = "session_enabled"

    @Synchronized
    fun load(context: Context): UxPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return normalize(
            UxPreferences(
                fontScale = normalizeFontScale(prefs.getFloat(KEY_FONT_SCALE, 1.0f)),
                compactMode = prefs.getBoolean(KEY_COMPACT_MODE, false),
                vibrateOnConnect = prefs.getBoolean(KEY_VIBRATE_CONNECT, true),
                vibrateOnError = prefs.getBoolean(KEY_VIBRATE_ERROR, true),
                silentSuccessFeedback = prefs.getBoolean(KEY_SILENT_SUCCESS, false),
                lastFocusStage = FocusStage.fromStored(
                    prefs.getString(KEY_LAST_FOCUS_STAGE, FocusStage.OFF.name)
                ),
                activeConnectionMode = ConnectionMode.fromStored(
                    prefs.getString(KEY_ACTIVE_CONNECTION_MODE, ConnectionMode.WIFI_DIRECT.name)
                ),
                connectionViewMode = ConnectionViewMode.fromStored(
                    prefs.getString(KEY_CONNECTION_VIEW_MODE, ConnectionViewMode.WIFI_DIRECT.name)
                ),
                wifiDirectModeEnabled = prefs.getBoolean(KEY_WIFI_DIRECT_MODE_ENABLED, true),
                lanModeEnabled = prefs.getBoolean(KEY_LAN_MODE_ENABLED, true),
                joinedGlobalLan = prefs.getBoolean(KEY_JOINED_GLOBAL_LAN, false),
                autoDownloadChannelFiles = prefs.getBoolean(KEY_AUTO_DOWNLOAD_CHANNEL_FILES, false),
                sessionEnabled = prefs.getBoolean(KEY_SESSION_ENABLED, true)
            )
        )
    }

    @Synchronized
    fun save(context: Context, preferences: UxPreferences): UxPreferences {
        // General preference saves may carry an old UI snapshot. Only explicit session actions
        // can change this flag, so changing theme or network mode cannot reopen a closed session.
        val normalized = prepareForSave(preferences, load(context).sessionEnabled)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putFloat(KEY_FONT_SCALE, normalized.fontScale)
                putBoolean(KEY_COMPACT_MODE, normalized.compactMode)
                putBoolean(KEY_VIBRATE_CONNECT, normalized.vibrateOnConnect)
                putBoolean(KEY_VIBRATE_ERROR, normalized.vibrateOnError)
                putBoolean(KEY_SILENT_SUCCESS, normalized.silentSuccessFeedback)
                putString(KEY_LAST_FOCUS_STAGE, normalized.lastFocusStage.name)
                putString(KEY_ACTIVE_CONNECTION_MODE, normalized.activeConnectionMode.name)
                putString(KEY_CONNECTION_VIEW_MODE, normalized.connectionViewMode.name)
                putBoolean(KEY_WIFI_DIRECT_MODE_ENABLED, normalized.wifiDirectModeEnabled)
                putBoolean(KEY_LAN_MODE_ENABLED, normalized.lanModeEnabled)
                putBoolean(KEY_JOINED_GLOBAL_LAN, normalized.joinedGlobalLan)
                putBoolean(KEY_AUTO_DOWNLOAD_CHANNEL_FILES, normalized.autoDownloadChannelFiles)
            }
        return normalized
    }

    internal fun prepareForSave(preferences: UxPreferences, currentSessionEnabled: Boolean): UxPreferences =
        normalize(preferences).copy(sessionEnabled = currentSessionEnabled)

    @Synchronized
    fun setSessionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_SESSION_ENABLED, enabled)
        }
    }

    fun normalizeFontScale(raw: Float): Float {
        return raw.coerceIn(0.85f, 1.25f)
    }

    private fun normalize(preferences: UxPreferences): UxPreferences {
        var activeMode = preferences.activeConnectionMode
        var viewMode = preferences.connectionViewMode
        var wifiDirectEnabled = preferences.wifiDirectModeEnabled
        var lanEnabled = preferences.lanModeEnabled

        if (!wifiDirectEnabled && !lanEnabled) {
            when (activeMode) {
                ConnectionMode.WIFI_DIRECT -> wifiDirectEnabled = true
                ConnectionMode.LAN -> lanEnabled = true
            }
        }

        if (activeMode == ConnectionMode.WIFI_DIRECT && !wifiDirectEnabled) {
            activeMode = if (lanEnabled) ConnectionMode.LAN else ConnectionMode.WIFI_DIRECT
        } else if (activeMode == ConnectionMode.LAN && !lanEnabled) {
            activeMode = if (wifiDirectEnabled) ConnectionMode.WIFI_DIRECT else ConnectionMode.LAN
        }

        // Older preferences can name LAN while retaining the simple Direct view (or vice versa).
        // Keep the visible transport and its routing policy aligned after applying availability fallbacks.
        if (viewMode != ConnectionViewMode.ADVANCED) {
            viewMode = ConnectionViewMode.fromConnectionMode(activeMode)
        }

        return preferences.copy(
            fontScale = normalizeFontScale(preferences.fontScale),
            activeConnectionMode = activeMode,
            connectionViewMode = viewMode,
            wifiDirectModeEnabled = wifiDirectEnabled,
            lanModeEnabled = lanEnabled,
            joinedGlobalLan = preferences.joinedGlobalLan && lanEnabled,
            autoDownloadChannelFiles = preferences.autoDownloadChannelFiles
        )
    }
}
