package com.mechanicalrooster.app.ui

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mechanicalrooster.app.AppContainer
import com.mechanicalrooster.app.BuildConfig
import com.mechanicalrooster.app.auth.GoogleSignIn
import com.mechanicalrooster.app.data.LoginRequest
import com.mechanicalrooster.app.data.SettingsDto
import com.mechanicalrooster.app.db.OpenTaskEntity
import com.mechanicalrooster.app.fuzzy.Fuzzy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(private val container: AppContainer) : ViewModel() {

    val session = container.session.sessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val openTasks: StateFlow<List<OpenTaskEntity>> = container.repository.openTasks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var quickAddText by mutableStateOf("")
    var titleHistory by mutableStateOf<List<String>>(emptyList())
        private set
    val suggestions by derivedStateOf {
        if (quickAddText.isBlank()) emptyList()
        else Fuzzy.rank(quickAddText, titleHistory).filterNot { it.equals(quickAddText, ignoreCase = true) }
    }

    var busy by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    val devLoginAvailable: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()

    fun signInWithGoogle(activityContext: Context, baseUrl: String) {
        signIn(baseUrl) { GoogleSignIn.getIdToken(activityContext, BuildConfig.GOOGLE_WEB_CLIENT_ID) }
    }

    /** Backend dev-bypass login for LAN testing before Google OAuth is configured. */
    fun signInAsDev(baseUrl: String) {
        signIn(baseUrl) { "dev-token" }
    }

    private fun signIn(baseUrl: String, idTokenProvider: suspend () -> String) {
        if (baseUrl.isBlank()) {
            errorMessage = "Enter the server URL first."
            return
        }
        launchBusy {
            container.session.saveBaseUrl(baseUrl)
            val idToken = idTokenProvider()
            val response = container.apiClient.api().login(LoginRequest(idToken))
            container.session.saveLogin(response.token, response.email, response.settings)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                container.repository.sync()
                titleHistory = container.repository.titles()
            } catch (e: Exception) {
                errorMessage = e.friendly()
            }
        }
    }

    fun addTask(title: String = quickAddText) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        quickAddText = ""
        launchBusy {
            container.repository.addTask(trimmed)
            titleHistory = container.repository.titles()
        }
    }

    fun completeTask(id: String) {
        viewModelScope.launch {
            container.repository.completeTask(id)
        }
    }

    fun saveSettings(initialDelayMinutes: Int, repeatIntervalMinutes: Int, onDone: () -> Unit) {
        launchBusy {
            val saved = container.repository.updateSettings(
                SettingsDto(initialDelayMinutes, repeatIntervalMinutes),
            )
            container.session.saveSettings(saved)
            onDone()
        }
    }

    fun signOut() {
        launchBusy {
            container.repository.signOut()
            container.session.clear()
            quickAddText = ""
            titleHistory = emptyList()
        }
    }

    fun canScheduleExactAlarms(): Boolean = container.scheduler.canScheduleExact()

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: Exception) {
                errorMessage = e.friendly()
            } finally {
                busy = false
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}

private fun Exception.friendly(): String = when (this) {
    is java.net.ConnectException, is java.net.SocketTimeoutException ->
        "Cannot reach the server. Check the URL and your network."
    is retrofit2.HttpException -> when (code()) {
        401 -> "Session rejected by the server. Try signing in again."
        else -> "Server error (${code()})."
    }
    else -> message ?: "Something went wrong."
}
