package com.pilerks1.hdrrecorder.data

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
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
    private val storage: RecordingStorageManager,
    private val reporter: ErrorReporter
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
            reporter.report(IssueCode.RECORDING_BUSY, "startRecording")
            return StartResult.Failed
        }

        Log.d("RecordingManager", "startRecording called")

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis())

        var output = try {
            storage.createOutput(destination, name)
        } catch (e: Exception) {
            reporter.report(IssueCode.RECORDING_START, "createRecordingOutput", e)
            return StartResult.Failed
        }
        val pendingRecording = try {
            try {
                prepareRecording(videoCapture, output)
            } catch (error: Exception) {
                if (output.destination !is RecordingDestination.DocumentTree) throw error
                output.abort()
                reporter.report(IssueCode.STORAGE_FALLBACK, "prepareRecording", error, mapOf("recovery" to "MediaStore"))
                output = storage.createOutput(RecordingDestination.Default, name)
                prepareRecording(videoCapture, output)
            }
        } catch (error: Exception) {
            output.abort()
            reporter.report(IssueCode.RECORDING_START, "prepareRecording", error)
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
            reporter.report(IssueCode.RECORDING_START, "startRecording", e)
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
            try {
                session.recording?.stop()
            } catch (error: Exception) {
                session.stopRequested = false
                reporter.report(IssueCode.RECORDING_CONTROL, "stopRecording", error)
            }
        }
    }

    fun pauseRecording(): Boolean = changeRecordingState("pauseRecording") { it.pause() }

    fun resumeRecording(): Boolean = changeRecordingState("resumeRecording") { it.resume() }

    private fun changeRecordingState(operation: String, change: (Recording) -> Unit): Boolean {
        val recording = activeRecording?.recording ?: return false
        return try {
            change(recording)
            true
        } catch (error: Exception) {
            reporter.report(IssueCode.RECORDING_CONTROL, operation, error)
            false
        }
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
                // Finalize is asynchronous: startup catches cannot handle failures here.
                var completionError: Exception? = null
                try {
                    session.output.complete(event.outputResults.outputUri, successful = !event.hasError())
                } catch (error: Exception) {
                    completionError = error
                } finally {
                    activeRecording = null
                    _isRecording.value = false
                }
                if (event.hasError()) {
                    completionError?.let { cleanup -> event.cause?.let { cleanup.addSuppressed(it) } }
                    reporter.report(IssueCode.RECORDING_FAILED, "finalizeRecording", completionError ?: event.cause,
                        mapOf("platformCode" to event.error.toString(), "recording" to "false"))
                } else if (completionError != null) {
                    reporter.report(IssueCode.RECORDING_METADATA, "finalizeMetadata", completionError)
                }
            }
        }
    }
}
