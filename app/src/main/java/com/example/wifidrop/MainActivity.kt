package com.example.wifidrop

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private var incomingShareEventId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Disable saved task screenshots; live launcher transitions remain controlled by Android.
            setRecentsScreenshotEnabled(false)
        }
        applySystemBarContrast()
        incomingShareEventId = IncomingShareBus.publishFromIntent(
            intent, savedInstanceState?.getLong(SHARE_DELIVERY_KEY)?.takeIf { it > 0L }
        )
        setContent {
            QetaraAppShell()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShareEventId = IncomingShareBus.publishFromIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        incomingShareEventId?.let { outState.putLong(SHARE_DELIVERY_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val SHARE_DELIVERY_KEY = "qetara.incomingShareDelivery"
    }

    private fun applySystemBarContrast() {
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}
