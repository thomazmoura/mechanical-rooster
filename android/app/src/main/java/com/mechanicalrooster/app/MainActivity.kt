package com.mechanicalrooster.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mechanicalrooster.app.ui.AppViewModel
import com.mechanicalrooster.app.ui.MainScreen
import com.mechanicalrooster.app.ui.SettingsScreen
import com.mechanicalrooster.app.ui.SignInScreen
import com.mechanicalrooster.app.ui.theme.RoosterTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory((application as RoosterApp).container)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoosterTheme {
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

private enum class Screen { Main, Settings }

@Composable
private fun App(viewModel: AppViewModel, requestNotificationPermission: () -> Unit) {
    val session by viewModel.session.collectAsState()
    var screen by remember { mutableStateOf(Screen.Main) }

    when {
        session == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        !session!!.isSignedIn -> SignInScreen(viewModel)

        screen == Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            session = session!!,
            onBack = { screen = Screen.Main },
        )

        else -> MainScreen(
            viewModel = viewModel,
            onOpenSettings = { screen = Screen.Settings },
            requestNotificationPermission = requestNotificationPermission,
        )
    }
}
