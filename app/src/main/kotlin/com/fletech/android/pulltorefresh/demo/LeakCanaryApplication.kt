package com.fletech.android.pulltorefresh.demo

import android.app.Application
import com.squareup.leakcanary.LeakCanary

/**
 * Created by flocsy on 17/01/2018.
 */

class LeakCanaryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return
        }
        LeakCanary.install(this)
        // Normal app init code...
    }
}
