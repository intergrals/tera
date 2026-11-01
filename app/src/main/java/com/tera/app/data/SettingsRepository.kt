package com.tera.app.data

import android.content.Context
import com.tera.app.domain.OverspendMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Currency
import java.util.Locale

data class AppSettings(
    val currencySymbol: String,
    val overspendMode: OverspendMode,
    val onboardingDone: Boolean,
)

/**
 * Single source of truth for app settings. Backed by SharedPreferences so the JSON backup
 * and the live UI read exactly the same values (no mirrored settings table).
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun current(): AppSettings = _settings.value

    fun setCurrencySymbol(symbol: String) {
        val clean = symbol.trim().take(3)
        prefs.edit().putString(KEY_CURRENCY, clean).apply()
        _settings.value = _settings.value.copy(currencySymbol = clean)
    }

    fun setOverspendMode(mode: OverspendMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(overspendMode = mode)
    }

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING, done).apply()
        _settings.value = _settings.value.copy(onboardingDone = done)
    }

    /** Replaces every setting at once (used by backup restore). */
    fun restore(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_CURRENCY, settings.currencySymbol)
            .putString(KEY_MODE, settings.overspendMode.name)
            .putBoolean(KEY_ONBOARDING, settings.onboardingDone)
            .apply()
        _settings.value = settings
    }

    private fun read(): AppSettings = AppSettings(
        currencySymbol = prefs.getString(KEY_CURRENCY, null) ?: defaultSymbol(),
        overspendMode = OverspendMode.fromStorage(prefs.getString(KEY_MODE, null)),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false),
    )

    private fun defaultSymbol(): String = runCatching {
        Currency.getInstance(Locale.getDefault()).symbol
    }.getOrNull()?.take(3) ?: "$"

    companion object {
        private const val NAME = "tera_settings"
        private const val KEY_CURRENCY = "currency_symbol"
        private const val KEY_MODE = "overspend_mode"
        private const val KEY_ONBOARDING = "onboarding_done"
    }
}
