package com.mora.shield

import android.app.Application
import com.mora.shield.core.MoraCore

class MoraShieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MoraCore.initialize(this)
    }
}
