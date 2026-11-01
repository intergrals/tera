package com.envelope.budget

import android.app.Application

/**
 * Application entry point. The dependency container is created lazily in [onCreate]
 * (manual DI — no annotation processor beyond Room).
 */
class BudgetApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
