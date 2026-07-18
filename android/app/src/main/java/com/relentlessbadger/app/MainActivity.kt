package com.relentlessbadger.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.relentlessbadger.app.ui.AppViewModel
import com.relentlessbadger.app.ui.CalendarScreen
import com.relentlessbadger.app.ui.MainScreen
import com.relentlessbadger.app.ui.SettingsScreen
import com.relentlessbadger.app.ui.SignInScreen
import com.relentlessbadger.app.ui.theme.BadgerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory((application as BadgerApp).container)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BadgerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    App(viewModel, ::requestNotificationPermission)
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Tasks("Tasks", Icons.Filled.Checklist),
    Calendar("Calendar", Icons.Filled.CalendarMonth),
}

@Composable
private fun App(viewModel: AppViewModel, requestNotificationPermission: () -> Unit) {
    val session by viewModel.session.collectAsState()
    // Above the when, so the selected tab survives the Settings round-trip.
    var tab by remember { mutableStateOf(Tab.Tasks) }
    var showSettings by remember { mutableStateOf(false) }

    when {
        session == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        !session!!.isSignedIn -> SignInScreen(viewModel)

        showSettings -> SettingsScreen(
            viewModel = viewModel,
            session = session!!,
            onBack = { showSettings = false },
        )

        else -> Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.Tasks -> MainScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings = true },
                        requestNotificationPermission = requestNotificationPermission,
                    )
                    Tab.Calendar -> CalendarScreen(viewModel)
                }
            }
        }
    }
}
