package com.pilerks1.hdrrecorder.compatibility

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CcViewModel(application: Application) : AndroidViewModel(application) {

    private val ccManager = CcManager(application)

    private val _compatibilityResult = MutableStateFlow<CompatibilityResult?>(null)
    val compatibilityResult = _compatibilityResult.asStateFlow()

    init {
        viewModelScope.launch {
            _compatibilityResult.value = ccManager.getCompatibilityData()
        }
    }
}
