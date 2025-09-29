package com.pilerks1.hdrrecorder.compatibility

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A simple sealed class to define the navigation destinations in the app.
 * This makes the navigation logic type-safe and saveable across configuration changes.
 */
@Parcelize
sealed class Screen : Parcelable {
    @Parcelize
    data object Camera : Screen()

    @Parcelize
    data object Compatibility : Screen()
}
