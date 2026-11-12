package com.pilerks1.hdrrecorder.camera.camerax

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import com.pilerks1.hdrrecorder.NANOS_PER_SECOND
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import com.pilerks1.hdrrecorder.data.storage.RecordingDestination
import com.pilerks1.hdrrecorder.data.storage.RecordingStorageManager
import com.pilerks1.hdrrecorder.data.telemetry.StatsManager
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the video recording process.
 * This class handles starting, stopping, pausing, and resuming recordings.
 * Recording duration is owned by StatsManager (fed via updateHardwareStats using the
 * hardware recording stream), so this class does not keep its own timer.
 *
 * Storage provider work (output creation and finalization metadata) runs on IO. [isRecording]
 * remains true until finalization completes, so recording-dependent operations stay gated.
 */
class CameraXRecordingManager(
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

    // Finalization must outlive the screen that started the recording.
    private val finalizationScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var activeRecording: ActiveRecording? = null
    private var nextRecordingId = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    /** Call from the main thread; the caller must not start another recording until this returns. */
    @SuppressLint("MissingPermission")
    suspend fun startRecording(videoCapture: VideoCapture<Recorder>, destination: RecordingDestination): StartResult {
        if (activeRecording != null) {
            reporter.report(IssueCode.RECORDING_BUSY, "startRecording")
            return StartResult.Failed
        }

        Log.d("CameraXRecordingManager", "startRecording called")

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis())

        // createOutput already falls back to MediaStore when the custom folder is unusable.
        var pendingOutput: RecordingStorageManager.RecordingOutput? = null
        var operation = "createRecordingOutput"
        return try {
            // Retain ownership even if cancellation discards the IO dispatcher's return value.
            withContext(Dispatchers.IO) { pendingOutput = storage.createOutput(destination, name) }
            val output = requireNotNull(pendingOutput)
            operation = "startRecording"
            val session = ActiveRecording(id = nextRecordingId++, output = output)
            session.recording = prepareRecording(videoCapture, output)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    handleRecordingEvent(session.id, event)
                }
            activeRecording = session
            pendingOutput = null // Finalization now owns the output.
            _isRecording.value = true
            StartResult.Started(output.destination)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            reporter.report(IssueCode.RECORDING_START, operation, e)
            StartResult.Failed
        } finally {
            pendingOutput?.let { output ->
                withContext(NonCancellable + Dispatchers.IO) { output.abort() }
            }
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
        Log.d("CameraXRecordingManager", "stopRecording called")
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
                    val deltaSeconds = deltaNanos / NANOS_PER_SECOND.toDouble()
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
                Log.d("CameraXRecordingManager", "Recording started")
            }
            is VideoRecordEvent.Finalize -> finalizationScope.launch {
                // Finalize is asynchronous: startup catches cannot handle failures here.
                var completionError: Exception? = null
                try {
                    withContext(Dispatchers.IO) {
                        session.output.complete(event.outputResults.outputUri, successful = !event.hasError())
                    }
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
