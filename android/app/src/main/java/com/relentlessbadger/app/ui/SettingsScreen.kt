package com.relentlessbadger.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.relentlessbadger.app.data.Session

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    session: Session,
    onBack: () -> Unit,
) {
    var initialDelay by rememberSaveable { mutableStateOf(session.initialDelayMinutes.toString()) }
    var repeatInterval by rememberSaveable { mutableStateOf(session.repeatIntervalMinutes.toString()) }
    var mediumWait by rememberSaveable { mutableStateOf(session.mediumWaitMinutes.toString()) }
    var longWait by rememberSaveable { mutableStateOf(session.longWaitMinutes.toString()) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var serverUrl by rememberSaveable { mutableStateOf(session.baseUrl) }
    var confirmServerChange by rememberSaveable { mutableStateOf(false) }
    val normalizedServerUrl = serverUrl.trim().trimEnd('/')

    val initialDelayValue = initialDelay.toIntOrNull()
    val repeatIntervalValue = repeatInterval.toIntOrNull()
    val mediumWaitValue = mediumWait.toIntOrNull()
    val longWaitValue = longWait.toIntOrNull()
    val valid = (initialDelayValue ?: 0) >= 1 && (repeatIntervalValue ?: 0) >= 1 &&
        (mediumWaitValue ?: 0) >= 1 && (longWaitValue ?: 0) >= 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "Defaults applied to every new task. Existing tasks keep the values they were created with.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = initialDelay,
                onValueChange = { initialDelay = it },
                label = { Text("First reminder after (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = initialDelay.isNotEmpty() && (initialDelayValue ?: 0) < 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = repeatInterval,
                onValueChange = { repeatInterval = it },
                label = { Text("Then nag every (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = repeatInterval.isNotEmpty() && (repeatIntervalValue ?: 0) < 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Snooze options shown on tasks and reminders. Pick how far each pushes the next nag.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = mediumWait,
                onValueChange = { mediumWait = it },
                label = { Text("Medium wait (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = mediumWait.isNotEmpty() && (mediumWaitValue ?: 0) < 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = longWait,
                onValueChange = { longWait = it },
                label = { Text("Long wait (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = longWait.isNotEmpty() && (longWaitValue ?: 0) < 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.saveSettings(
                        initialDelayValue!!,
                        repeatIntervalValue!!,
                        mediumWaitValue!!,
                        longWaitValue!!,
                        onDone = onBack,
                    )
                },
                enabled = valid && !viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Signed in as ${session.email ?: "unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.signOut() },
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign out")
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide advanced" else "Advanced")
            }

            if (showAdvanced) {
                ServerUrlField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { confirmServerChange = true },
                    enabled = !viewModel.busy && normalizedServerUrl.isNotBlank() &&
                        normalizedServerUrl != session.baseUrl,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Change server URL")
                }
            }

            viewModel.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmServerChange) {
        AlertDialog(
            onDismissRequest = { confirmServerChange = false },
            title = { Text("Change server?") },
            text = {
                Text(
                    "Your current session may be rejected by the new server, and you " +
                        "may need to sign in again. Your tasks stay on this device " +
                        "and will sync to the new server.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmServerChange = false
                        viewModel.changeServerUrl(serverUrl)
                    },
                ) {
                    Text("Change server")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmServerChange = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
