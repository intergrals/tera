package com.tera.app

import android.content.Context
import com.tera.app.data.BackupRepository
import com.tera.app.data.BudgetRepository
import com.tera.app.data.SettingsRepository
import com.tera.app.data.db.AppDatabase

/**
 * Manual dependency container. Everything the UI needs is reachable from here; there is
 * no service-locator singleton and no annotation-processor DI beyond Room.
 */
class AppContainer(private val appContext: Context) {

    val applicationContext: Context get() = appContext

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val budget: BudgetRepository by lazy { BudgetRepository(database, settings) }
    val backup: BackupRepository by lazy { BackupRepository(database, settings) }
}
