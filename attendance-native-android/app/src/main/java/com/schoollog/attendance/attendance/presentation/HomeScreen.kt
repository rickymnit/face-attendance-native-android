package com.schoollog.attendance.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onStartGateMode: () -> Unit,
    onOpenEnrollment: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecognitionQa: () -> Unit = {},
    onOpenPilotReadiness: () -> Unit = {},
    onOpenAndroidStressTest: () -> Unit = {},
    showRecognitionQa: Boolean = false,
    showPilotReadiness: Boolean = false,
    showAndroidStressTest: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Schoollog Attendance Kiosk",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onStartGateMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
            ) {
                Text(text = "Start Gate Mode")
            }
            OutlinedButton(
                onClick = onOpenEnrollment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(text = "Enrollment")
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(text = "Settings")
            }
            if (showRecognitionQa) {
                OutlinedButton(
                    onClick = onOpenRecognitionQa,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(text = "Recognition QA")
                }
            }
            if (showPilotReadiness) {
                OutlinedButton(
                    onClick = onOpenPilotReadiness,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(text = "Pilot Readiness")
                }
            }
            if (showAndroidStressTest) {
                OutlinedButton(
                    onClick = onOpenAndroidStressTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(text = "Android Stress Test")
                }
            }
        }
    }
}
