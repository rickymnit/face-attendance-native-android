package com.schoollog.attendance.ml.face

import android.graphics.Bitmap

class BoundingBoxFaceAlignment : FaceAlignment {
    override fun align(
        croppedBitmap: Bitmap,
        metadata: FaceCropMetadata,
    ): Bitmap = croppedBitmap
}
