package com.schoollog.attendance.ml.liveness

interface LivenessEngine {
    fun evaluate(frameSequence: List<LivenessFrameSample>): LivenessResult
}
