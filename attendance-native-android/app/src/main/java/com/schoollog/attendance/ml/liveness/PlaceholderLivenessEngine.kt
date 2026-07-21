package com.schoollog.attendance.ml.liveness

class PlaceholderLivenessEngine : LivenessEngine {
    override fun evaluate(frameSequence: List<LivenessFrameSample>): LivenessResult {
        if (frameSequence.size < RequiredFrameSequenceSize) {
            return LivenessResult(
                decision = LivenessDecision.UNCERTAIN,
                score = frameSequence.size / RequiredFrameSequenceSize.toFloat(),
                reason = "Collecting live frame sequence",
            )
        }

        return LivenessResult(
            decision = LivenessDecision.PASS,
            score = 0.88f,
            reason = "Placeholder liveness passed",
        )
    }

    private companion object {
        const val RequiredFrameSequenceSize = 6
    }
}
