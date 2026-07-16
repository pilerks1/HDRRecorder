package com.pilerks1.hdrrecorder.data.camera

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.guava.await

/**
 * Process-wide access to CameraX's singleton provider. Consumers may await readiness without
 * binding or unbinding use cases, which keeps capability queries separate from the live camera.
 */
object CameraProviderRepository {
    @Volatile
    private var providerFuture: ListenableFuture<ProcessCameraProvider>? = null

    fun getFuture(context: Context): ListenableFuture<ProcessCameraProvider> =
        providerFuture ?: synchronized(this) {
            providerFuture ?: ProcessCameraProvider.getInstance(context.applicationContext)
                .also { providerFuture = it }
        }

    suspend fun awaitProvider(context: Context): ProcessCameraProvider = getFuture(context).await()
}
