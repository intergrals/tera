package com.envelope.budget.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.envelope.budget.AppContainer

/** The app's dependency container, provided once at the root of the composition. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided")
}
