package com.n0tavailable.tunedetective

import android.app.Application
import com.google.android.material.color.DynamicColors

class MaterialU : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}