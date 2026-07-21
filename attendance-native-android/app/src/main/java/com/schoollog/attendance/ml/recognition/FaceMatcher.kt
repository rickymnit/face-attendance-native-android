package com.schoollog.attendance.ml.recognition

interface FaceMatcher {
    fun match(request: FaceMatchRequest): FaceMatchResult
}
