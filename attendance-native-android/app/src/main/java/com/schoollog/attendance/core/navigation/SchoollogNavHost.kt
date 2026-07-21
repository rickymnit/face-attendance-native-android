package com.schoollog.attendance.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoollog.attendance.BuildConfig
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.schoollog.attendance.app.SchoollogAttendanceApp
import com.schoollog.attendance.app.SettingsScreen
import com.schoollog.attendance.app.pilot.PilotReadinessScreen
import com.schoollog.attendance.app.setup.DeviceSetupScreen
import com.schoollog.attendance.attendance.presentation.HomeScreen
import com.schoollog.attendance.camera.presentation.GateCameraScreen
import com.schoollog.attendance.debugqa.presentation.AndroidStressTestScreen
import com.schoollog.attendance.debugqa.presentation.RecognitionQaScreen
import com.schoollog.attendance.enrollment.presentation.EnrollmentScreen

@Composable
fun SchoollogNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val appContainer = (LocalContext.current.applicationContext as SchoollogAttendanceApp).appContainer
    val deviceBinding by appContainer.deviceBindingRepository.deviceBinding.collectAsStateWithLifecycle(initialValue = null)

    if (deviceBinding == null) {
        DeviceSetupScreen()
        return
    }

    NavHost(
        navController = navController,
        startDestination = AppDestination.Home.route,
        modifier = modifier,
    ) {
        composable(AppDestination.Home.route) {
            HomeScreen(
                onStartGateMode = { navController.navigate(AppDestination.GateCamera.route) },
                onOpenEnrollment = { navController.navigate(AppDestination.Enrollment.route) },
                onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                onOpenRecognitionQa = { navController.navigate(AppDestination.RecognitionQa.route) },
                onOpenPilotReadiness = { navController.navigate(AppDestination.PilotReadiness.route) },
                onOpenAndroidStressTest = { navController.navigate(AppDestination.AndroidStressTest.route) },
                showRecognitionQa = BuildConfig.DEBUG,
                showPilotReadiness = BuildConfig.DEBUG,
                showAndroidStressTest = BuildConfig.DEBUG,
            )
        }
        composable(AppDestination.GateCamera.route) {
            GateCameraScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Enrollment.route) {
            EnrollmentScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        if (BuildConfig.DEBUG) {
            composable(AppDestination.RecognitionQa.route) {
                RecognitionQaScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestination.PilotReadiness.route) {
                PilotReadinessScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestination.AndroidStressTest.route) {
                AndroidStressTestScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
