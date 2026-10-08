package com.neonbear.cave

import android.app.Application

class CaveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
    }
}
