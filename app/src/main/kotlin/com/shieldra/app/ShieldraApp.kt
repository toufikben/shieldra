package com.shieldra.app

import android.app.Application

class ShieldraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Phase 3 — no initialization side effects.
        // Future phases: attach logging, but no analytics/telemetry in V1.
    }
}
