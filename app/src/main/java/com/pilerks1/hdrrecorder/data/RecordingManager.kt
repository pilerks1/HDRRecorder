package com.pilerks1.hdrrecorder.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri

/**
 * Manages the video recording process.
 * This class handles starting, stopping, pausing, and resuming recordings.
 * Recording duration is owned by StatsManager (fed via updateHardwareStats using the
 * hardware recording stream), so this class does not keep its own timer.
 */
class RecordingManager(private val context: Context, private val statsManager: StatsManager) {

    private var activeRecording: Recording? = null

    // Maintain a reference to the active file descriptor so we can close it when finished
    private var activePfd: ParcelFileDescriptor? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var lastBytes = 0L
    private var lastDurationNanos = 0L

    @SuppressLint("MissingPermission")
    fun startRecording(videoCapture: VideoCapture<Recorder>, storageUri: String?) {
        Log.d("RecordingManager", "startRecording called")
        _isRecording.value = true

        // Reset trackers for new session
        lastBytes = 0L
        lastDurationNanos = 0L

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis()) + ".mp4"

        var pendingRecording: PendingRecording? = null

        // 1. Attempt to use custom Storage URI (SAF) if provided by user
        if (storageUri != null) {
            try {
                val treeUri = storageUri.toUri()
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
    }

    fun stopRecording() {
        Log.d("RecordingManager", "stopRecording called")
        activeRecording?.stop()
        activeRecording = null
        _isRecording.value = false
    }

    fun pauseRecording() {
        Log.d("RecordingManager", "pauseRecording called")
        activeRecording?.pause()
    }

    fun resumeRecording() {
        Log.d("RecordingManager", "resumeRecording called")
        activeRecording?.resume()
    }

    private val videoRecordingListener = Consumer<VideoRecordEvent> { event ->
        when (event) {
            is VideoRecordEvent.Status -> {
                val stats = event.recordingStats
                val bytes = stats.numBytesRecorded
                val durationNanos = stats.recordedDurationNanos
                
                // 1. Calculate precise bitrate using hardware deltas
                var bitrate = 0.0
                if (lastDurationNanos > 0L) {
                    val deltaBytes = bytes - lastBytes
                    val deltaNanos = durationNanos - lastDurationNanos
                    val deltaSeconds = deltaNanos / 1_000_000_000.0
                    if (deltaSeconds > 0) {
                        bitrate = (deltaBytes * 8.0) / (deltaSeconds * 1_000_000.0)
                    }
                }
                
                lastBytes = bytes
                lastDurationNanos = durationNanos

                // 2. Update StatsManager with real hardware numbers and calculated bitrate.
                // This hardware duration is the single source of truth for recording time.
                statsManager.updateHardwareStats(bytes, durationNanos, bitrate)
            }
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