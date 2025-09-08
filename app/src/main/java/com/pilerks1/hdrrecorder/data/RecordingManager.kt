package com.pilerks1.hdrrecorder.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
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

    private val _recordingTimeSeconds = MutableStateFlow(0L)
    val recordingTimeSeconds = _recordingTimeSeconds.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(videoCapture: VideoCapture<Recorder>) {
        Log.d("RecordingManager", "startRecording called")
        _isRecording.value = true

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/HDR-Recorder")
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        activeRecording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
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
                if (!event.hasError()) {
                    val msg = "Video saved to: ${event.outputResults.outputUri}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.d("RecordingManager", msg)
                } else {
                    activeRecording?.close()
                    activeRecording = null
                    Log.e("RecordingManager", "Video capture error: ${event.error}")
                }
            }
        }
    }
}
