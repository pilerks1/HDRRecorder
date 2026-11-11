package com.pilerks1.hdrrecorder.data.compatibility

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import android.content.Context
import android.os.Build
import android.util.Range
import androidx.camera.camera2.interop.cameraId
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalMimeTypeApi
import androidx.camera.video.Recorder
import com.pilerks1.hdrrecorder.BuildConfig
import com.pilerks1.hdrrecorder.data.CaptureConfiguration
import com.pilerks1.hdrrecorder.data.PreviewConfiguration
import com.pilerks1.hdrrecorder.data.RecordingConfiguration
import com.pilerks1.hdrrecorder.data.RecordingSessionConfiguration
import com.pilerks1.hdrrecorder.data.camera.DefaultBackCameraRepository
import com.pilerks1.hdrrecorder.data.camera.core.CameraCatalog
import com.pilerks1.hdrrecorder.data.storage.PrivateDocumentStore
import com.pilerks1.hdrrecorder.data.storage.StorageManager.Companion.COMPATIBILITY_REPORT_ID
import com.pilerks1.hdrrecorder.model.*
import com.pilerks1.hdrrecorder.model.CompatibilityStatus as S
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/** Capability queries only. Constructing a use case never attaches it to a camera. */
class CompatibilityScanner(
    private val context: Context,
    private val store: PrivateDocumentStore,
    private val reporter: ErrorReporter
) {
    val environment: String = listOf(Build.MANUFACTURER, Build.MODEL, Build.FINGERPRINT,
        Build.VERSION.SDK_INT, BuildConfig.VERSION_CODE, BuildConfig.CAMERAX_VERSION).joinToString("|")

    suspend fun read(): CompatibilityReport? {
        val json = store.read(COMPATIBILITY_REPORT_ID) ?: return null
        return try { CompatibilityReportJson.decode(json) } catch (error: Exception) {
            reporter.report(IssueCode.COMPATIBILITY_CACHE, "readCompatibilityReport", error, notify = false)
            null
        }
    }

    @androidx.annotation.OptIn(ExperimentalMimeTypeApi::class)
    suspend fun scan(previous: CompatibilityReport?, onProgress: (CompatibilityReport) -> Unit): CompatibilityReport =
        withContext(Dispatchers.Default) {
            val provider = ProcessCameraProvider.getInstance(context).await()
            val info = DefaultBackCameraRepository(context).cameraInfo(provider)
            val cameraId = info.cameraId
            fun <T> query(source: String, block: () -> T): T? = try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                reporter.report(IssueCode.CAPABILITY_QUERY, "compatibilityQuery", error,
                    mapOf("query" to source), notify = false)
                null
            }
            val videoCodecs = Recorder.getSupportedVideoMimeTypes().distinct()
            val audioCodecs = Recorder.getSupportedAudioMimeTypes().distinct()
            val codecs = listOf<String?>(null) + videoCodecs
            val unsupportedCodecs = mutableSetOf<String?>()
            val capabilities = codecs.associateWith { codec ->
                query("Recorder.getVideoCapabilities(${codec ?: "Auto"})") {
                    RecordingConfiguration.videoCapabilities(info, codec).also { if (it == null) unsupportedCodecs += codec }
                }
            }
            val colorsByCodec = codecs.associateWith { codec ->
                capabilities[codec]?.let { caps -> query("VideoCapabilities.getSupportedDynamicRanges($codec)") {
                    RecordingConfiguration.colors(caps).map { color ->
                        CompatibilityColor(color.encoding.storageId, color.bitDepth,
                            "${color.encoding.displayName} ${color.bitDepth}-bit")
                    }
                } }
            }
            val colors = colorsByCodec.values.filterNotNull().flatten().distinct()
                .sortedWith(compareBy({ it.depth }, { it.encoding }))
            check(colors.isNotEmpty()) { "No recording capabilities" }
            val videoStabilization = capabilities[null]?.let { caps ->
                query("VideoCapabilities.isStabilizationSupported") { caps.isStabilizationSupported }
            }
            val previewStabilization = query("PreviewCapabilities.isStabilizationSupported") {
                Preview.getPreviewCapabilities(info).isStabilizationSupported
            }
            val boost = query("CameraInfo.isLowLightBoostSupported") { info.isLowLightBoostSupported }
            val preview = query("PreviewConfiguration.queryOptions") { PreviewConfiguration.queryOptions(info) }
            val collector = Camera2ReportCollector(CameraCatalog.get(context).camera(cameraId).metadata.getOrThrow(), reporter)
            val camera2 = collector.collect(cameraId) + CompatibilityTable("Preview · CameraX", listOf("Field", "Value"), listOf(
                listOf("Dynamic ranges", preview?.dynamicRanges?.joinToString { it.displayName } ?: S.UNKNOWN.label),
                listOf("General FPS", preview?.frameRates?.joinToString { "[${it.lower}, ${it.upper}]" } ?: S.UNKNOWN.label),
                listOf("Stabilization", support(previewStabilization))
            ))
            val cameraX = listOf(
                CompatibilityTable("Capabilities", listOf("Field", "Value"), listOf(
                    listOf("Video stabilization", support(videoStabilization)),
                    listOf("Preview stabilization", support(previewStabilization)),
                    listOf("AE boost", support(boost))
                )),
                CompatibilityTable("Video codecs", listOf("Codec"), listOf(listOf("Auto")) + videoCodecs.map { listOf(it) }),
                CompatibilityTable("Audio codecs", listOf("Codec"), listOf(listOf("Auto")) + audioCodecs.map { listOf(it) }),
                CompatibilityTable("Environment", listOf("Field", "Value"), listOf(
                    listOf("Device", "${Build.MANUFACTURER} ${Build.MODEL}"),
                    listOf("Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}"),
                    listOf("CameraX", BuildConfig.CAMERAX_VERSION), listOf("App", BuildConfig.VERSION_NAME)
                ))
            )
            val candidates = buildList {
                for (ratio in RecordingAspectRatio.entries) for (codec in codecs)
                    for (stabilization in listOf(false, true)) for (resolution in Resolution.entries)
                        for (color in colors) for (fps in CompatibilityConfiguration.SCAN_FPS) {
                            add(CompatibilityConfiguration(ratio.storageId, codec, stabilization,
                                resolution.storageId, color.encoding, color.depth, fps))
                        }
            }
            val keys = candidates.map { it.key }.toSet()
            val resumable = previous?.takeIf {
                it.completedAt == null && it.environment == environment && it.cameraId == cameraId &&
                    it.total == candidates.size && it.colors == colors && it.videoCodecs == videoCodecs &&
                    it.combinations.all { result -> result.configuration.key in keys }
            }
            var report = CompatibilityReport(environment = environment, cameraId = cameraId,
                startedAt = resumable?.startedAt ?: System.currentTimeMillis(), total = candidates.size,
                videoCodecs = videoCodecs, audioCodecs = audioCodecs, colors = colors,
                cameraX = cameraX, camera2 = camera2, combinations = resumable?.combinations.orEmpty())
            suspend fun checkpoint() {
                store.write(COMPATIBILITY_REPORT_ID, CompatibilityReportJson.encode(report))
                onProgress(report)
            }
            checkpoint()
            val completed = report.combinations.associateBy { it.configuration.key }
            val results = report.combinations.toMutableList()
            val qualitySupport = mutableMapOf<String, Boolean?>()
            for (config in candidates) {
                currentCoroutineContext().ensureActive()
                if (config.key in completed) continue
                val color = colors.first { it.encoding == config.encoding && it.depth == config.depth }
                val capture = CaptureConfiguration(
                    fps = config.fps,
                    aspectRatio = RecordingAspectRatio.fromStorageId(config.aspectRatio)!!,
                    resolution = Resolution.fromStorageId(config.resolution)!!,
                    videoCodec = config.codec, encoding = VideoEncoding.fromStorageId(config.encoding)!!,
                    bitDepth = config.depth,
                    isStabilizationEnabled = config.stabilization
                )
                val caps = capabilities[config.codec]
                val supportedColors = colorsByCodec[config.codec]
                var status = when {
                    config.codec in unsupportedCodecs -> S.UNSUPPORTED
                    caps == null || supportedColors == null -> S.UNKNOWN
                    color !in supportedColors -> S.UNSUPPORTED
                    else -> S.SUPPORTED
                }
                if (caps != null && status == S.SUPPORTED) {
                    val qualityKey = "${config.codec}|${config.resolution}|${config.encoding}|${config.depth}"
                    if (!qualitySupport.containsKey(qualityKey)) {
                        qualitySupport[qualityKey] = query("VideoCapabilities quality($qualityKey)") {
                            RecordingConfiguration.isResolutionSupported(caps, capture)
                        }
                    }
                    status = when (qualitySupport[qualityKey]) { true -> S.SUPPORTED; false -> S.UNSUPPORTED; null -> S.UNKNOWN }
                }
                if (config.stabilization) {
                    if (videoStabilization == false || previewStabilization == false) status = S.UNSUPPORTED
                    else if ((videoStabilization == null || previewStabilization == null) && status != S.UNSUPPORTED) status = S.UNKNOWN
                }
                if (status == S.SUPPORTED) {
                    try {
                        // Feature groups cannot coexist with the explicit production use-case options.
                        val session = SessionConfig.Builder(listOf(
                            RecordingSessionConfiguration.preview(capture, 0),
                            RecordingSessionConfiguration.video(capture, 0)
                        )).build()
                        if (!info.isSessionConfigSupported(session) ||
                            Range(config.fps, config.fps) !in info.getSupportedFrameRateRanges(session)
                        ) status = S.UNSUPPORTED
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        status = S.UNKNOWN
                        reporter.report(IssueCode.CAPABILITY_QUERY, "compatibilitySession", error, notify = false)
                    }
                }
                val boostStatus = when {
                    status == S.UNSUPPORTED || boost == false || config.fps > 30 -> S.UNSUPPORTED
                    status == S.UNKNOWN || boost == null -> S.UNKNOWN
                    else -> S.CONDITIONAL
                }
                results += CompatibilityCombination(config, status, boostStatus)
                report = report.copy(combinations = results.toList())
                if (results.size % 24 == 0) checkpoint() else onProgress(report)
            }
            report = report.copy(completedAt = System.currentTimeMillis())
            checkpoint()
            report
        }

    private fun support(value: Boolean?): String = when (value) {
        true -> S.SUPPORTED.label; false -> S.UNSUPPORTED.label; null -> S.UNKNOWN.label
    }
}
