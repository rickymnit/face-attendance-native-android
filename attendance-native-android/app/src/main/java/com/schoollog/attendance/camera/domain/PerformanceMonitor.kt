package com.schoollog.attendance.camera.domain

import android.os.SystemClock
import com.schoollog.attendance.ml.recognition.FaceMatchResult
import com.schoollog.attendance.ml.recognition.RecognitionDecision
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PerformanceMonitor {
    private val lock = Any()
    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()
    private val _benchmarkState = MutableStateFlow(PipelineBenchmarkState())
    val benchmarkState: StateFlow<PipelineBenchmarkState> = _benchmarkState.asStateFlow()

    private var inputWindowStartedAtMillis = SystemClock.elapsedRealtime()
    private var inputFrameCount = 0
    private var processedWindowStartedAtMillis = SystemClock.elapsedRealtime()
    private var processedFrameCount = 0
    private var faceDetectionSamples = 0L
    private var qualitySamples = 0L
    private var stableTrackingSamples = 0L
    private var livenessSamples = 0L
    private var faceCropSamples = 0L
    private var embeddingSamples = 0L
    private var matchingSamples = 0L
    private var pipelineDecisionSamples = 0L
    private var pipelineStartedAtMillis: Long? = null
    private var stableDecisionStartedAtMillis: Long? = null
    private var droppedFrameTotal = 0L
    private var activeBenchmark: MutableBenchmark? = null

    fun markInputFrame(nowMillis: Long = SystemClock.elapsedRealtime()) {
        synchronized(lock) {
            inputFrameCount += 1
            val elapsed = nowMillis - inputWindowStartedAtMillis
            if (elapsed >= FpsWindowMillis) {
                val fps = inputFrameCount * 1000.0 / elapsed
                updateMetrics { copy(analyzerInputFps = fps) }
                activeBenchmark?.analyzerInputFps?.add(fps)
                inputFrameCount = 0
                inputWindowStartedAtMillis = nowMillis
            }
        }
    }

    fun markDroppedFrame() {
        synchronized(lock) {
            droppedFrameTotal += 1
            activeBenchmark?.droppedFrames = (activeBenchmark?.droppedFrames ?: 0L) + 1L
            updateMetrics { copy(droppedFrameCount = droppedFrameCount + 1) }
        }
    }

    fun markProcessedFrame(nowMillis: Long = SystemClock.elapsedRealtime()) {
        synchronized(lock) {
            processedFrameCount += 1
            activeBenchmark?.memoryUsedMb?.add(usedMemoryMb())
            val elapsed = nowMillis - processedWindowStartedAtMillis
            if (elapsed >= FpsWindowMillis) {
                val fps = processedFrameCount * 1000.0 / elapsed
                updateMetrics { copy(processedFps = fps, memoryUsedMb = usedMemoryMb()) }
                activeBenchmark?.processedFps?.add(fps)
                processedFrameCount = 0
                processedWindowStartedAtMillis = nowMillis
            }
        }
    }

    fun recordFaceDetection(startedAtNanos: Long) {
        synchronized(lock) {
            val elapsed = elapsedMillis(startedAtNanos)
            faceDetectionSamples += 1
            activeBenchmark?.faceDetectionMillis?.add(elapsed)
            updateMetrics { copy(averageFaceDetectionMillis = average(averageFaceDetectionMillis, faceDetectionSamples, elapsed)) }
        }
    }

    fun recordQualityEvaluation(startedAtNanos: Long) {
        synchronized(lock) {
            val elapsed = elapsedMillis(startedAtNanos)
            qualitySamples += 1
            activeBenchmark?.faceQualityMillis?.add(elapsed)
            updateMetrics { copy(averageQualityEvaluationMillis = average(averageQualityEvaluationMillis, qualitySamples, elapsed)) }
        }
    }

    fun recordStableTracking(startedAtNanos: Long) {
        synchronized(lock) {
            val elapsed = elapsedMillis(startedAtNanos)
            stableTrackingSamples += 1
            activeBenchmark?.stableTrackingMillis?.add(elapsed)
            updateMetrics { copy(averageStableTrackingMillis = average(averageStableTrackingMillis, stableTrackingSamples, elapsed)) }
        }
    }

    fun recordLiveness(startedAtNanos: Long) {
        synchronized(lock) {
            val elapsed = elapsedMillis(startedAtNanos)
            livenessSamples += 1
            activeBenchmark?.livenessMillis?.add(elapsed)
            updateMetrics { copy(averageLivenessMillis = average(averageLivenessMillis, livenessSamples, elapsed)) }
        }
    }

    fun recordFaceCrop(startedAtNanos: Long) {
        synchronized(lock) {
            val elapsed = elapsedMillis(startedAtNanos)
            faceCropSamples += 1
            activeBenchmark?.faceCropMillis?.add(elapsed)
            updateMetrics { copy(averageFaceCropMillis = average(averageFaceCropMillis, faceCropSamples, elapsed)) }
        }
    }

    fun recordEmbeddingInference(elapsedMillis: Double) {
        synchronized(lock) {
            embeddingSamples += 1
            activeBenchmark?.embeddingInferenceMillis?.add(elapsedMillis)
            updateMetrics { copy(averageEmbeddingInferenceMillis = average(averageEmbeddingInferenceMillis, embeddingSamples, elapsedMillis)) }
        }
    }

    fun recordMatching(elapsedMillis: Double) {
        synchronized(lock) {
            matchingSamples += 1
            activeBenchmark?.localMatchingMillis?.add(elapsedMillis)
            updateMetrics { copy(averageMatchingMillis = average(averageMatchingMillis, matchingSamples, elapsedMillis)) }
        }
    }

    fun recordMatchResult(matchResult: FaceMatchResult) {
        synchronized(lock) {
            matchingSamples += 1
            activeBenchmark?.localMatchingMillis?.add(matchResult.matchingTimeMillis)
            updateMetrics {
                copy(
                    averageMatchingMillis = average(averageMatchingMillis, matchingSamples, matchResult.matchingTimeMillis),
                    lastTopMatchScores = matchResult.topMatches.take(3).map { it.score },
                )
            }
        }
    }

    fun recordPipelineOutput(output: LiveFramePipelineOutput) {
        synchronized(lock) {
            val hasOneFace = output.faceDetectionResult?.hasExactlyOneFace == true
            val stableReady = output.stableFaceTrackingResult?.isReadyForLiveness == true
            if (hasOneFace && pipelineStartedAtMillis == null) {
                pipelineStartedAtMillis = SystemClock.elapsedRealtime()
            }
            if (stableReady && stableDecisionStartedAtMillis == null) {
                stableDecisionStartedAtMillis = SystemClock.elapsedRealtime()
            }
            if (!hasOneFace) {
                pipelineStartedAtMillis = null
                stableDecisionStartedAtMillis = null
            }

            output.failureReason()?.let { reason ->
                updateMetrics { copy(lastFailureReason = reason) }
            }

            if (output.isDecision()) {
                val now = SystemClock.elapsedRealtime()
                val stableElapsed = stableDecisionStartedAtMillis?.let { (now - it).coerceAtLeast(0L).toDouble() }
                val faceElapsed = pipelineStartedAtMillis?.let { (now - it).coerceAtLeast(0L).toDouble() }
                val decisionElapsed = stableElapsed ?: faceElapsed
                if (decisionElapsed != null) {
                    pipelineDecisionSamples += 1
                    activeBenchmark?.totalDecisionMillis?.add(decisionElapsed)
                    updateMetrics {
                        copy(averagePipelineDecisionMillis = average(averagePipelineDecisionMillis, pipelineDecisionSamples, decisionElapsed))
                    }
                }
                if (output.recognitionDecision == RecognitionDecision.MATCH_ACCEPTED) {
                    activeBenchmark?.successCount = (activeBenchmark?.successCount ?: 0) + 1
                } else {
                    activeBenchmark?.failureCount = (activeBenchmark?.failureCount ?: 0) + 1
                }
                pipelineStartedAtMillis = null
                stableDecisionStartedAtMillis = null
            }
        }
    }

    fun startBenchmark(
        deviceModel: String,
        androidVersion: String,
        durationMillis: Long = BenchmarkDurationMillis,
    ) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            activeBenchmark = MutableBenchmark(
                sessionId = UUID.randomUUID().toString(),
                startedAtMillis = now,
                durationMillis = durationMillis,
                deviceModel = deviceModel,
                androidVersion = androidVersion,
            )
            _benchmarkState.value = PipelineBenchmarkState(
                isRunning = true,
                sessionId = activeBenchmark?.sessionId,
                startedAtMillis = now,
                durationMillis = durationMillis,
                result = null,
                statusMessage = "Benchmark running",
            )
        }
    }

    fun finishBenchmark() {
        synchronized(lock) {
            val benchmark = activeBenchmark ?: return
            val result = benchmark.toResult(System.currentTimeMillis())
            activeBenchmark = null
            _benchmarkState.value = PipelineBenchmarkState(
                isRunning = false,
                sessionId = result.sessionId,
                startedAtMillis = result.startedAtMillis,
                durationMillis = result.durationMillis,
                result = result,
                statusMessage = if (result.targetMet && result.noAnalyzerBacklog) {
                    "Benchmark completed: target met"
                } else {
                    "Benchmark completed: review performance"
                },
            )
        }
    }

    fun cancelBenchmark() {
        synchronized(lock) {
            activeBenchmark = null
            _benchmarkState.value = _benchmarkState.value.copy(
                isRunning = false,
                statusMessage = "Benchmark cancelled",
            )
        }
    }

    fun benchmarkCsv(): String {
        val result = benchmarkState.value.result ?: return "status\nno_benchmark_result\n"
        return buildString {
            appendLine("sessionId,startedAtMillis,completedAtMillis,durationMillis,deviceModel,androidVersion,droppedFrames,successCount,failureCount,noAnalyzerBacklog,targetMet")
            appendLine(listOf(result.sessionId.csv(), result.startedAtMillis, result.completedAtMillis, result.durationMillis, result.deviceModel.csv(), result.androidVersion.csv(), result.droppedFrames, result.successCount, result.failureCount, result.noAnalyzerBacklog, result.targetMet).joinToString(","))
            appendLine("metric,average,p95,worst")
            appendMetric("analyzerInputFps", result.analyzerInputFps)
            appendMetric("processedFps", result.processedFps)
            appendMetric("faceDetectionMillis", result.faceDetectionMillis)
            appendMetric("faceQualityMillis", result.faceQualityMillis)
            appendMetric("stableTrackingMillis", result.stableTrackingMillis)
            appendMetric("livenessMillis", result.livenessMillis)
            appendMetric("faceCropMillis", result.faceCropMillis)
            appendMetric("embeddingInferenceMillis", result.embeddingInferenceMillis)
            appendMetric("localMatchingMillis", result.localMatchingMillis)
            appendMetric("totalDecisionMillis", result.totalDecisionMillis)
            appendMetric("memoryUsedMb", result.memoryUsedMb)
        }
    }

    private fun StringBuilder.appendMetric(name: String, summary: BenchmarkMetricSummary) {
        appendLine(listOf(name, summary.average, summary.p95, summary.worst).joinToString(","))
    }

    private fun LiveFramePipelineOutput.isDecision(): Boolean =
        recognitionDecision == RecognitionDecision.MATCH_ACCEPTED ||
            recognitionDecision == RecognitionDecision.NO_MATCH ||
            recognitionDecision == RecognitionDecision.LOW_CONFIDENCE ||
            recognitionDecision == RecognitionDecision.AMBIGUOUS_MATCH ||
            recognitionDecision == RecognitionDecision.NO_EMBEDDINGS_LOADED ||
            recognitionDecision == RecognitionDecision.MODEL_VERSION_MISMATCH ||
            recognitionDecision == RecognitionDecision.LIVENESS_FAILED ||
            recognitionDecision == RecognitionDecision.ERROR

    private fun LiveFramePipelineOutput.failureReason(): String? =
        when {
            faceDetectionResult?.quality?.passes == false -> faceDetectionResult.quality.reason
            livenessResult?.decision?.name == "FAIL" -> livenessResult.reason
            recognitionDecision == RecognitionDecision.NO_MATCH -> "No matching student"
            recognitionDecision == RecognitionDecision.LOW_CONFIDENCE -> "Recognition confidence too low"
            recognitionDecision == RecognitionDecision.AMBIGUOUS_MATCH -> "Ambiguous local face match"
            recognitionDecision == RecognitionDecision.NO_EMBEDDINGS_LOADED -> "No active local embeddings loaded"
            recognitionDecision == RecognitionDecision.MODEL_VERSION_MISMATCH -> "Embedding model version mismatch"
            recognitionDecision == RecognitionDecision.MULTIPLE_FACES -> "Multiple faces detected"
            recognitionDecision == RecognitionDecision.ERROR -> "Pipeline error"
            else -> null
        }

    private fun updateMetrics(block: PerformanceMetrics.() -> PerformanceMetrics) {
        _metrics.value = _metrics.value.block()
    }

    private fun average(
        currentAverage: Double,
        sampleCount: Long,
        newValue: Double,
    ): Double = currentAverage + (newValue - currentAverage) / sampleCount

    private fun elapsedMillis(startedAtNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedAtNanos).coerceAtLeast(0L) / NanosPerMillis.toDouble()

    private fun usedMemoryMb(): Double {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L) / BytesPerMegabyte.toDouble()
    }

    private fun List<Double>.summary(): BenchmarkMetricSummary {
        if (isEmpty()) return BenchmarkMetricSummary()
        val sorted = sorted()
        val p95Index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)
        return BenchmarkMetricSummary(
            average = average(),
            p95 = sorted[p95Index],
            worst = sorted.last(),
        )
    }

    private fun String.csv(): String = "\"" + replace("\"", "\"\"") + "\""

    private data class MutableBenchmark(
        val sessionId: String,
        val startedAtMillis: Long,
        val durationMillis: Long,
        val deviceModel: String,
        val androidVersion: String,
        val analyzerInputFps: MutableList<Double> = mutableListOf(),
        val processedFps: MutableList<Double> = mutableListOf(),
        val faceDetectionMillis: MutableList<Double> = mutableListOf(),
        val faceQualityMillis: MutableList<Double> = mutableListOf(),
        val stableTrackingMillis: MutableList<Double> = mutableListOf(),
        val livenessMillis: MutableList<Double> = mutableListOf(),
        val faceCropMillis: MutableList<Double> = mutableListOf(),
        val embeddingInferenceMillis: MutableList<Double> = mutableListOf(),
        val localMatchingMillis: MutableList<Double> = mutableListOf(),
        val totalDecisionMillis: MutableList<Double> = mutableListOf(),
        val memoryUsedMb: MutableList<Double> = mutableListOf(),
        var droppedFrames: Long = 0L,
        var successCount: Int = 0,
        var failureCount: Int = 0,
    ) {
        fun toResult(completedAtMillis: Long): PipelineBenchmarkResult = PipelineBenchmarkResult(
            sessionId = sessionId,
            startedAtMillis = startedAtMillis,
            completedAtMillis = completedAtMillis,
            durationMillis = durationMillis,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            analyzerInputFps = analyzerInputFps.summary(),
            processedFps = processedFps.summary(),
            faceDetectionMillis = faceDetectionMillis.summary(),
            faceQualityMillis = faceQualityMillis.summary(),
            stableTrackingMillis = stableTrackingMillis.summary(),
            livenessMillis = livenessMillis.summary(),
            faceCropMillis = faceCropMillis.summary(),
            embeddingInferenceMillis = embeddingInferenceMillis.summary(),
            localMatchingMillis = localMatchingMillis.summary(),
            totalDecisionMillis = totalDecisionMillis.summary(),
            memoryUsedMb = memoryUsedMb.summary(),
            droppedFrames = droppedFrames,
            successCount = successCount,
            failureCount = failureCount,
            noAnalyzerBacklog = droppedFrames == 0L,
        )
    }

    private const val FpsWindowMillis = 1_000L
    private const val BenchmarkDurationMillis = 30_000L
    private const val NanosPerMillis = 1_000_000L
    private const val BytesPerMegabyte = 1024L * 1024L
}
