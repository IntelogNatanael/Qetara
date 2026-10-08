package com.example.wifidrop

import android.app.Application

class QetaraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStrings.initialize(this)
    }
}
