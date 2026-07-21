package com.schoollog.attendance.ml.face

import android.graphics.Bitmap

interface FaceAlignment {
    fun align(
        croppedBitmap: Bitmap,
        metadata: FaceCropMetadata,
    ): Bitmap
}
