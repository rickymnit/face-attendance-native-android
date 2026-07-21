package com.schoollog.attendance.core.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class CameraPermissionState(
    val isGranted: Boolean,
    val requestPermission: () -> Unit,
)

@Composable
fun CameraPermissionHandler(
    content: @Composable (CameraPermissionState) -> Unit,
) {
    val context = LocalContext.current
    var isGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        isGranted = granted
    }
    val requestPermission = { launcher.launch(Manifest.permission.CAMERA) }

    LaunchedEffect(Unit) {
        if (!isGranted) {
            requestPermission()
        }
    }

    content(
        CameraPermissionState(
            isGranted = isGranted,
            requestPermission = requestPermission,
        ),
    )
}
