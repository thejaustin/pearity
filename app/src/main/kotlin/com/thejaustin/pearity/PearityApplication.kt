package com.thejaustin.pearity

import android.app.Application
import com.thejaustin.pearity.utils.CrashHandler

class PearityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.setup(this)
    }
}
