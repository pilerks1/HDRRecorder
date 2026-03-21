package com.pilerks1.hdrrecorder.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages the video recording process.
 * This class handles starting, stopping, pausing, and resuming recordings.
 * It also manages the recording timer and communicates recording status
 * back to the application.
 */
class RecordingManager(private val context: Context) {

    private var activeRecording: Recording? = null
    private var recordingJob: Job? = null

    // Maintain a reference to the active file descriptor so we can close it when finished
    private var activePfd: ParcelFileDescriptor? = null

    private val _recordingTimeSeconds = MutableStateFlow(0L)
    val recordingTimeSeconds = _recordingTimeSeconds.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(videoCapture: VideoCapture<Recorder>, storageUri: String?) {
        Log.d("RecordingManager", "startRecording called")
        _isRecording.value = true

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis()) + ".mp4"

        var pendingRecording: PendingRecording? = null

        // 1. Attempt to use custom Storage URI (SAF) if provided by user
        if (storageUri != null) {
            try {
                val treeUri = Uri.parse(storageUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val newFileUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    docUri,
                    "video/mp4",
                    name
                )

                if (newFileUri != null) {
                    activePfd = context.contentResolver.openFileDescriptor(newFileUri, "rw")
                    if (activePfd != null) {
                        // Create specific FileDescriptor options
                        val outputOptions = FileDescriptorOutputOptions.Builder(activePfd!!).build()
                        // Resolve prepareRecording specifically for FileDescriptorOutputOptions
                        pendingRecording = videoCapture.output.prepareRecording(context, outputOptions)
                    }
                }
            } catch (e: Exception) {
                Log.e("RecordingManager", "Failed to setup SAF custom storage", e)
            }
        }

        // 2. Fallback to default MediaStore standard collection if SAF fails or isn't set
        if (pendingRecording == null) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/HDR-Recorder")
            }

            // Create specific MediaStore options
            val outputOptions = MediaStoreOutputOptions
                .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build()

            // Resolve prepareRecording specifically for MediaStoreOutputOptions
            pendingRecording = videoCapture.output.prepareRecording(context, outputOptions)
        }

        // Now that the compiler successfully resolved PendingRecording, we can apply audio and start
        activeRecording = pendingRecording
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context), videoRecordingListener)

        startTimer()
    }

    fun stopRecording() {
        Log.d("RecordingManager", "stopRecording called")
        activeRecording?.stop()
        activeRecording = null
        stopTimer()
        _isRecording.value = false
    }

    fun pauseRecording() {
        Log.d("RecordingManager", "pauseRecording called")
        activeRecording?.pause()
        recordingJob?.cancel() // Pause the timer
    }

    fun resumeRecording() {
        Log.d("RecordingManager", "resumeRecording called")
        activeRecording?.resume()
        startTimer() // Resume the timer
    }

    private fun startTimer() {
        recordingJob?.cancel() // Cancel any existing timer
        recordingJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(1000)
                _recordingTimeSeconds.value++
            }
        }
    }

    private fun stopTimer() {
        recordingJob?.cancel()
        _recordingTimeSeconds.value = 0
    }

    private val videoRecordingListener = Consumer<VideoRecordEvent> { event ->
        when (event) {
            is VideoRecordEvent.Start -> {
                Log.d("RecordingManager", "Recording started")
            }
            is VideoRecordEvent.Finalize -> {
                // VERY IMPORTANT: Close the file descriptor manually for SAF recording
                activePfd?.close()
                activePfd = null

                if (!event.hasError()) {
                    val msg = "Video saved successfully"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.d("RecordingManager", "Video saved to: ${event.outputResults.outputUri}")
                } else {
                    activeRecording?.close()
                    activeRecording = null
                    Log.e("RecordingManager", "Video capture error: ${event.error}")
                }
            }
        }
    }
}