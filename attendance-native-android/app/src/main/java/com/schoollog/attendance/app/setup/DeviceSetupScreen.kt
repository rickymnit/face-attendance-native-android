package com.schoollog.attendance.app.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schoollog.attendance.app.SchoollogAttendanceApp

@Composable
fun DeviceSetupScreen() {
    val appContainer = (LocalContext.current.applicationContext as SchoollogAttendanceApp).appContainer
    val viewModel: DeviceSetupViewModel = viewModel(
        factory = DeviceSetupViewModel.factory(
            syncApi = appContainer.syncApi,
            deviceBindingRepository = appContainer.deviceBindingRepository,
            settingsRepository = appContainer.settingsRepository,
            embeddingSyncRepository = appContainer.embeddingSyncRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DeviceSetupContent(
        uiState = uiState,
        onSchoolCodeChanged = viewModel::onSchoolCodeChanged,
        onGateIdChanged = viewModel::onGateIdChanged,
        onDeviceNameChanged = viewModel::onDeviceNameChanged,
        onSetupTokenChanged = viewModel::onSetupTokenChanged,
        onRegisterDevice = viewModel::registerDevice,
    )
}

@Composable
private fun DeviceSetupContent(
    uiState: DeviceSetupUiState,
    onSchoolCodeChanged: (String) -> Unit,
    onGateIdChanged: (String) -> Unit,
    onDeviceNameChanged: (String) -> Unit,
    onSetupTokenChanged: (String) -> Unit,
    onRegisterDevice: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Bind Gate Device",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Schoollog support should bind this phone to one school and one gate before Gate Mode is used.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )
            OutlinedTextField(
                value = uiState.schoolCode,
                onValueChange = onSchoolCodeChanged,
                label = { Text(text = "School code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.gateId,
                onValueChange = onGateIdChanged,
                label = { Text(text = "Gate name / Gate ID") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            OutlinedTextField(
                value = uiState.deviceName,
                onValueChange = onDeviceNameChanged,
                label = { Text(text = "Device name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            OutlinedTextField(
                value = uiState.setupToken,
                onValueChange = onSetupTokenChanged,
                label = { Text(text = "Setup token / Admin login placeholder") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            uiState.statusMessage?.let { message ->
                Text(
                    text = message,
                    color = if (message.startsWith("Device registered")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Button(
                onClick = onRegisterDevice,
                enabled = !uiState.isRegistering,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
            ) {
                Text(text = if (uiState.isRegistering) "Registering..." else "Register Device")
            }
        }
    }
}
