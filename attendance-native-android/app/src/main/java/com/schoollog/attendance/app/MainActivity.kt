package com.schoollog.attendance.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.core.navigation.SchoollogNavHost
import com.schoollog.attendance.core.ui.SchoollogAttendanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()

        setContent {
            SchoollogAttendanceTheme {
                SchoollogNavHost()
            }
        }
    }
}
