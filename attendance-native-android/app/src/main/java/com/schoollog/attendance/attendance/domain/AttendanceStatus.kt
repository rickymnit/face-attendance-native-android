package com.schoollog.attendance.attendance.domain

import com.schoollog.attendance.core.common.AttendanceRules
import java.time.LocalTime

object AttendanceStatusCalculator {
    fun statusFor(
        now: LocalTime,
        rules: AttendanceRules,
    ): String =
        when {
            now >= rules.halfDayAfterTime.toLocalTimeOrDefault("10:30") -> "HALF_DAY"
            now >= rules.lateAfterTime.toLocalTimeOrDefault("08:15") -> "LATE"
            else -> "PRESENT"
        }

    private fun String.toLocalTimeOrDefault(defaultValue: String): LocalTime =
        runCatching { LocalTime.parse(this) }
            .getOrDefault(LocalTime.parse(defaultValue))
}
