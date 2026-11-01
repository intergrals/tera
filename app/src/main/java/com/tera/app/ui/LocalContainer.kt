package com.tera.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.tera.app.AppContainer

/** The app's dependency container, provided once at the root of the composition. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided")
}
