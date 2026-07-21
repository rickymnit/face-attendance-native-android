package com.schoollog.attendance.ml.face

import android.graphics.Bitmap

data class FaceCropResult(
    val bitmap: Bitmap?,
    val metadata: FaceCropMetadata?,
    val failureReason: FaceCropFailureReason,
) {
    val isSuccess: Boolean = failureReason == FaceCropFailureReason.SUCCESS && bitmap != null
}
