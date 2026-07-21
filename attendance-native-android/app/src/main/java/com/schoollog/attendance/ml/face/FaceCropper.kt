package com.schoollog.attendance.ml.face

interface FaceCropper {
    fun cropFace(request: FaceCropRequest): FaceCropResult
}
