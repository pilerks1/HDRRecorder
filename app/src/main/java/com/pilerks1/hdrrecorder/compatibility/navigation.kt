package com.pilerks1.hdrrecorder.compatibility

/**
 * A simple sealed class to define the navigation destinations in the app.
 * This makes the navigation logic type-safe.
 */
sealed class Screen {
    object Camera : Screen()
    object Compatibility : Screen()
}
