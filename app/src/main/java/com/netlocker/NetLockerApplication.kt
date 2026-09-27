package com.netlocker

import android.app.Application
import com.netlocker.util.ServiceLocator

class NetLockerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
