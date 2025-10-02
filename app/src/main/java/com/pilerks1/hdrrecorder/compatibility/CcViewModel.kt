package com.pilerks1.hdrrecorder.compatibility

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CcViewModel(application: Application) : AndroidViewModel(application) {

    private val ccManager = CcManager(application)

    private val _compatibilityResult = MutableStateFlow<CompatibilityResult?>(null)
    val compatibilityResult = _compatibilityResult.asStateFlow()

    init {
        // Launch a coroutine to perform the compatibility check on a background thread
        viewModelScope.launch {
            _compatibilityResult.value = withContext(Dispatchers.IO) {
                try {
                    ccManager.getCompatibilityData()
                } catch (e: Exception) {
                    // Handle cases where the camera cannot be initialized or fails
                    null
                }
            }
        }
    }
}