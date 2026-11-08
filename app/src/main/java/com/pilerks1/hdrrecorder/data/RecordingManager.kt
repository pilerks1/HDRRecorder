package com.pilerks1.hdrrecorder.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import com.pilerks1.hdrrecorder.data.storage.RecordingDestination
import com.pilerks1.hdrrecorder.data.storage.RecordingStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages the video recording process.
 * This class handles starting, stopping, pausing, and resuming recordings.
 * Recording duration is owned by StatsManager (fed via updateHardwareStats using the
 * hardware recording stream), so this class does not keep its own timer.
 */
class RecordingManager(
    private val context: Context,
    private val statsManager: StatsManager,
    private val storage: RecordingStorageManager
) {
    sealed interface StartResult {
        data class Started(val destination: RecordingDestination) : StartResult
        data object Failed : StartResult
    }

    private data class ActiveRecording(
        val id: Long,
        val output: RecordingStorageManager.RecordingOutput,
        var recording: Recording? = null,
        var stopRequested: Boolean = false,
        var lastBytes: Long = 0L,
        var lastDurationNanos: Long = 0L
    )

    private var activeRecording: ActiveRecording? = null
    private var nextRecordingId = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(videoCapture: VideoCapture<Recorder>, destination: RecordingDestination): StartResult {
        if (activeRecording != null) {
            Log.w("RecordingManager", "Ignoring start request while another recording is finalizing")
            return StartResult.Failed
        }

        Log.d("RecordingManager", "startRecording called")

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis())

        var output = try {
            storage.createOutput(destination, name)
        } catch (e: Exception) {
            Log.e("RecordingManager", "Failed to prepare recording output", e)
            return StartResult.Failed
        }
        val pendingRecording = try {
            try {
                prepareRecording(videoCapture, output)
            } catch (error: Exception) {
                if (output.destination !is RecordingDestination.DocumentTree) throw error
                output.abort()
                Log.e("RecordingManager", "Failed to prepare SAF recording; using MediaStore", error)
                output = storage.createOutput(RecordingDestination.Default, name)
                prepareRecording(videoCapture, output)
            }
        } catch (error: Exception) {
            output.abort()
            Log.e("RecordingManager", "Failed to prepare recording", error)
            return StartResult.Failed
        }
        val session = ActiveRecording(id = nextRecordingId++, output = output)
        activeRecording = session

        return try {
            session.recording = pendingRecording
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    handleRecordingEvent(session.id, event)
                }
            _isRecording.value = true
            StartResult.Started(output.destination)
        } catch (e: Exception) {
            activeRecording = null
            output.abort()
            Log.e("RecordingManager", "Failed to start recording", e)
            StartResult.Failed
        }
    }

    private fun prepareRecording(
        videoCapture: VideoCapture<Recorder>,
        output: RecordingStorageManager.RecordingOutput
    ): PendingRecording = when (val options = output.options) {
        is FileDescriptorOutputOptions -> videoCapture.output.prepareRecording(context, options)
        is MediaStoreOutputOptions -> videoCapture.output.prepareRecording(context, options)
        else -> error("Unsupported recording output")
    }

    fun stopRecording() {
        Log.d("RecordingManager", "stopRecording called")
        val session = activeRecording ?: return
        if (!session.stopRequested) {
            session.stopRequested = true
            session.recording?.stop()
        }
    }

    fun pauseRecording() {
        Log.d("RecordingManager", "pauseRecording called")
        activeRecording?.recording?.pause()
    }

    fun resumeRecording() {
        Log.d("RecordingManager", "resumeRecording called")
        activeRecording?.recording?.resume()
    }

    private fun handleRecordingEvent(recordingId: Long, event: VideoRecordEvent) {
        val session = activeRecording?.takeIf { it.id == recordingId } ?: return
        when (event) {
            is VideoRecordEvent.Status -> {
                val stats = event.recordingStats
                val bytes = stats.numBytesRecorded
                val durationNanos = stats.recordedDurationNanos
                
                // 1. Calculate precise bitrate using hardware deltas
                var bitrate = 0.0
                if (session.lastDurationNanos > 0L) {
                    val deltaBytes = bytes - session.lastBytes
                    val deltaNanos = durationNanos - session.lastDurationNanos
                    val deltaSeconds = deltaNanos / 1_000_000_000.0
                    if (deltaSeconds > 0) {
                        bitrate = (deltaBytes * 8.0) / (deltaSeconds * 1_000_000.0)
                    }
                }
                
                session.lastBytes = bytes
                session.lastDurationNanos = durationNanos

                // 2. Update StatsManager with real hardware numbers and calculated bitrate.
                // This hardware duration is the single source of truth for recording time.
                statsManager.updateHardwareStats(bytes, durationNanos, bitrate)
            }
            is VideoRecordEvent.Start -> {
                Log.d("RecordingManager", "Recording started")
            }
            is VideoRecordEvent.Finalize -> {
                if (!event.hasError()) {
                    val msg = try {
                        session.output.complete(event.outputResults.outputUri, successful = true)
                        "Video saved successfully"
                    } catch (error: Exception) {
                        Log.w("RecordingManager", "Video saved but output metadata could not be updated", error)
                        "Video saved, but its filename or MIME type could not be updated"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.d("RecordingManager", "Video saved to: ${event.outputResults.outputUri}")
                } else {
                    session.output.complete(event.outputResults.outputUri, successful = false)
                    Log.e("RecordingManager", "Video capture error: ${event.error}")
                    Toast.makeText(context, "Video recording failed (error ${event.error})", Toast.LENGTH_LONG).show()
                }
                activeRecording = null
                _isRecording.value = false
            }
        }
    }
}
