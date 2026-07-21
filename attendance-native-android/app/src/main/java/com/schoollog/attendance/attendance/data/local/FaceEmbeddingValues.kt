package com.schoollog.attendance.attendance.data.local

object FaceEmbeddingStatusValues {
    const val Active = "ACTIVE"
    const val PendingApproval = "PENDING_APPROVAL"
    const val Deleted = "DELETED"
}

object FaceEmbeddingSourceValues {
    const val BulkImport = "BULK_IMPORT"
    const val AppEnrollment = "APP_ENROLLMENT"
    const val ReEnrollment = "RE_ENROLLMENT"
    const val DebugPlaceholder = "DEBUG_PLACEHOLDER"
}
