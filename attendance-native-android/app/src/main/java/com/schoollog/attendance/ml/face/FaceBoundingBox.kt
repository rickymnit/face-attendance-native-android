package com.schoollog.attendance.ml.face

data class FaceBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float = right - left
    val height: Float = bottom - top
    val area: Float = width.coerceAtLeast(0f) * height.coerceAtLeast(0f)
}
