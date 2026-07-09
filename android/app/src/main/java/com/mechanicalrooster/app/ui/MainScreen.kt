package com.mechanicalrooster.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mechanicalrooster.app.db.OpenTaskEntity
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AppViewModel,
    onOpenSettings: () -> Unit,
    requestNotificationPermission: () -> Unit,
) {
    val tasks by viewModel.openTasks.collectAsState()
    val session by viewModel.session.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        requestNotificationPermission()
        viewModel.refresh()
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MechanicalRooster") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            if (!viewModel.canScheduleExactAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Exact alarms are disabled, so reminders may arrive late.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        }) {
                            Text("Allow exact alarms")
                        }
                    }
                }
            }

            QuickAdd(viewModel)

            Spacer(Modifier.height(8.dp))

            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(64.dp))
                    Text("Nothing pending 🎉", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add something above and the rooster starts crowing.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            mediumWaitMinutes = session?.mediumWaitMinutes ?: 60,
                            longWaitMinutes = session?.longWaitMinutes ?: 240,
                            onDone = { viewModel.completeTask(task.id) },
                            onSnooze = { minutes -> viewModel.snoozeTask(task.id, minutes) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAdd(viewModel: AppViewModel) {
    Column {
        OutlinedTextField(
            value = viewModel.quickAddText,
            onValueChange = { viewModel.quickAddText = it },
            placeholder = { Text("What needs doing right away?") },
            singleLine = true,
            trailingIcon = {
                FilledTonalIconButton(
                    onClick = { viewModel.addTask() },
                    enabled = viewModel.quickAddText.isNotBlank() && !viewModel.busy,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add task")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        if (viewModel.suggestions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column {
                    viewModel.suggestions.forEach { suggestion ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.addTask(suggestion) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                suggestion,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: OpenTaskEntity,
    mediumWaitMinutes: Int,
    longWaitMinutes: Int,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "next nag ${relativeFuture(task.nextFireAtMillis)} · every ${task.repeatIntervalMinutes} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.Snooze, contentDescription = "Snooze")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Medium wait (${formatDuration(mediumWaitMinutes)})") },
                    onClick = {
                        menuExpanded = false
                        onSnooze(mediumWaitMinutes)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Long wait (${formatDuration(longWaitMinutes)})") },
                    onClick = {
                        menuExpanded = false
                        onSnooze(longWaitMinutes)
                    },
                )
            }
        }

        FilledTonalIconButton(onClick = onDone) {
            Icon(Icons.Filled.Check, contentDescription = "Mark done")
        }
    }
}

private fun relativeFuture(epochMillis: Long): String {
    val remaining = epochMillis - System.currentTimeMillis()
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "in $minutes min"
        minutes < 60 * 24 -> "in ${minutes / 60} h"
        else -> "in ${minutes / (60 * 24)} d"
    }
}

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
