package com.schoollog.attendance.attendance.domain

import java.util.concurrent.atomic.AtomicLong

object FaceEmbeddingCacheVersion {
    private val version = AtomicLong(0L)

    fun current(): Long = version.get()

    fun markChanged(): Long = version.incrementAndGet()
}
