package com.pilerks1.hdrrecorder.compatibility

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CcViewModel(application: Application) : AndroidViewModel(application) {

    private val ccManager = CcManager(application)

    private val _compatibilityResult = MutableStateFlow<CompatibilityResult?>(null)
    val compatibilityResult = _compatibilityResult.asStateFlow()

    init {
        // Since the logic is now simple and synchronous, we can just call it directly.
        // It returns an empty table structure.
        _compatibilityResult.value = ccManager.getCompatibilityData()
    }
}

