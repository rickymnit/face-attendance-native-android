package com.schoollog.attendance.ml.liveness

import android.os.SystemClock
import com.schoollog.attendance.camera.domain.PerformanceMonitor
import kotlin.math.abs
import kotlin.math.hypot

class LivenessEngineV0 : LivenessEngine {
    override fun evaluate(frameSequence: List<LivenessFrameSample>): LivenessResult {
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        return try {
            evaluateInternal(frameSequence)
        } finally {
            PerformanceMonitor.recordLiveness(startedAtNanos)
        }
    }

    private fun evaluateInternal(frameSequence: List<LivenessFrameSample>): LivenessResult {
        if (frameSequence.isEmpty()) {
            return uncertain(0f, "Collecting live frame sequence")
        }

        if (frameSequence.any { !it.faceDetectionResult.hasExactlyOneFace }) {
            return failed("Multiple faces or missing face during liveness")
        }

        if (frameSequence.any { !it.faceDetectionResult.quality.passes }) {
            return failed("Face quality failed during liveness")
        }

        if (frameSequence.size < MinimumFrameCount) {
            return uncertain(
                score = frameSequence.size / MinimumFrameCount.toFloat(),
                reason = "Collecting live frame sequence",
            )
        }

        val elapsedMillis = frameSequence.elapsedMillis()
        if (elapsedMillis < MinimumSequenceMillis) {
            return uncertain(
                score = (elapsedMillis / MinimumSequenceMillis.toFloat()).coerceIn(0f, 1f),
                reason = "Collecting live frame sequence",
            )
        }

        val faceSamples = frameSequence.mapNotNull { sample ->
            sample.faceDetectionResult.primaryFace?.let { face -> sample to face }
        }
        if (faceSamples.size != frameSequence.size) {
            return failed("Face disappeared during liveness")
        }

        val centerMovement = normalizedCenterMovementRange(faceSamples)
        val angleMovement = headAngleRange(faceSamples)
        val boxMovement = boundingBoxAreaRange(faceSamples)
        val eyeMovement = eyeOpennessRange(faceSamples)

        val hasNaturalMotion = centerMovement >= MinimumCenterMovement ||
            angleMovement >= MinimumAngleMovementDegrees ||
            boxMovement >= MinimumBoxAreaMovement ||
            (eyeMovement != null && eyeMovement >= MinimumEyeMovement)

        if (!hasNaturalMotion) {
            return failed("Liveness failed: face sequence is too static")
        }

        val score = weightedScore(
            centerMovement = centerMovement,
            angleMovement = angleMovement,
            boxMovement = boxMovement,
            eyeMovement = eyeMovement,
            elapsedMillis = elapsedMillis,
        )

        return LivenessResult(
            decision = LivenessDecision.PASS,
            score = score,
            reason = "Liveness passed",
        )
    }

    private fun List<LivenessFrameSample>.elapsedMillis(): Long {
        val first = first().frameInfo.timestampNanos
        val last = last().frameInfo.timestampNanos
        return ((last - first) / NanosPerMillis).coerceAtLeast(0L)
    }

    private fun normalizedCenterMovementRange(
        samples: List<Pair<LivenessFrameSample, com.schoollog.attendance.ml.face.DetectedFace>>,
    ): Float {
        val centers = samples.map { (sample, face) ->
            val frameWidth = sample.frameInfo.analysisWidth.coerceAtLeast(1).toFloat()
            val frameHeight = sample.frameInfo.analysisHeight.coerceAtLeast(1).toFloat()
            val centerX = (face.boundingBox.left + face.boundingBox.right) / 2f / frameWidth
            val centerY = (face.boundingBox.top + face.boundingBox.bottom) / 2f / frameHeight
            centerX to centerY
        }
        val first = centers.first()
        return centers.maxOf { (x, y) -> hypot((x - first.first).toDouble(), (y - first.second).toDouble()).toFloat() }
    }

    private fun headAngleRange(
        samples: List<Pair<LivenessFrameSample, com.schoollog.attendance.ml.face.DetectedFace>>,
    ): Float {
        val first = samples.first().second
        return samples.maxOf { (_, face) ->
            maxOf(
                abs(face.headEulerAngleX - first.headEulerAngleX),
                abs(face.headEulerAngleY - first.headEulerAngleY),
                abs(face.headEulerAngleZ - first.headEulerAngleZ),
            )
        }
    }

    private fun boundingBoxAreaRange(
        samples: List<Pair<LivenessFrameSample, com.schoollog.attendance.ml.face.DetectedFace>>,
    ): Float {
        val areas = samples.map { (sample, face) ->
            val frameArea = sample.frameInfo.analysisWidth.coerceAtLeast(1).toFloat() * sample.frameInfo.analysisHeight.coerceAtLeast(1).toFloat()
            (face.boundingBox.width * face.boundingBox.height / frameArea).coerceAtLeast(0f)
        }
        val first = areas.first().coerceAtLeast(0.0001f)
        return areas.maxOf { abs(it - first) / first }
    }

    private fun eyeOpennessRange(
        samples: List<Pair<LivenessFrameSample, com.schoollog.attendance.ml.face.DetectedFace>>,
    ): Float? {
        val values = samples.mapNotNull { (_, face) ->
            val eyes = listOfNotNull(face.leftEyeOpenProbability, face.rightEyeOpenProbability)
            if (eyes.isEmpty()) null else eyes.average().toFloat()
        }
        if (values.size < MinimumEyeSamples) return null
        return values.maxOrNull()?.minus(values.minOrNull() ?: return null)
    }

    private fun weightedScore(
        centerMovement: Float,
        angleMovement: Float,
        boxMovement: Float,
        eyeMovement: Float?,
        elapsedMillis: Long,
    ): Float {
        val centerScore = (centerMovement / PreferredCenterMovement).coerceIn(0f, 1f)
        val angleScore = (angleMovement / PreferredAngleMovementDegrees).coerceIn(0f, 1f)
        val boxScore = (boxMovement / PreferredBoxAreaMovement).coerceIn(0f, 1f)
        val eyeScore = eyeMovement?.let { (it / PreferredEyeMovement).coerceIn(0f, 1f) } ?: 0.65f
        val durationScore = (elapsedMillis / PreferredSequenceMillis.toFloat()).coerceIn(0f, 1f)
        return ((centerScore * 0.25f) +
            (angleScore * 0.25f) +
            (boxScore * 0.20f) +
            (eyeScore * 0.15f) +
            (durationScore * 0.15f)).coerceIn(0f, 1f)
    }

    private fun uncertain(score: Float, reason: String): LivenessResult =
        LivenessResult(
            decision = LivenessDecision.UNCERTAIN,
            score = score.coerceIn(0f, 1f),
            reason = reason,
        )

    private fun failed(reason: String): LivenessResult =
        LivenessResult(
            decision = LivenessDecision.FAIL,
            score = 0f,
            reason = reason,
        )

    private companion object {
        const val NanosPerMillis = 1_000_000L
        const val MinimumFrameCount = 6
        const val MinimumEyeSamples = 4
        const val MinimumSequenceMillis = 1_000L
        const val PreferredSequenceMillis = 1_500L
        const val MinimumCenterMovement = 0.006f
        const val MinimumAngleMovementDegrees = 1.2f
        const val MinimumBoxAreaMovement = 0.012f
        const val MinimumEyeMovement = 0.025f
        const val PreferredCenterMovement = 0.035f
        const val PreferredAngleMovementDegrees = 6f
        const val PreferredBoxAreaMovement = 0.08f
        const val PreferredEyeMovement = 0.12f
    }
}
