package com.relentlessbadger.app.ui

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
import com.relentlessbadger.app.AppContainer
import com.relentlessbadger.app.BuildConfig
import com.relentlessbadger.app.auth.GoogleSignIn
import com.relentlessbadger.app.data.LoginRequest
import com.relentlessbadger.app.data.Recurrence
import com.relentlessbadger.app.data.SettingsDto
import com.relentlessbadger.app.db.OpenTaskEntity
import com.relentlessbadger.app.fuzzy.Fuzzy
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

    /** Optional absolute time (epoch millis) for the next task's first reminder. */
    var quickAddFirstWarningAtMillis by mutableStateOf<Long?>(null)

    /** Optional recurrence rule for the next task; requires a first-reminder time. */
    var quickAddRecurrence by mutableStateOf<Recurrence?>(null)

    /** Task whose schedule is being edited in the dialog, if any. */
    var editingTask by mutableStateOf<OpenTaskEntity?>(null)

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

    /**
     * Syncs with the backend. Being offline is normal now, so connectivity
     * errors are only surfaced when the user explicitly asked ([interactive]);
     * everything else (e.g. a rejected session) is always shown.
     */
    fun refresh(interactive: Boolean = false) {
        viewModelScope.launch {
            try {
                container.repository.sync()
            } catch (e: Exception) {
                if (interactive || e !is java.io.IOException) {
                    errorMessage = e.friendly()
                }
            }
            titleHistory = container.repository.titles()
        }
    }

    fun addTask(title: String = quickAddText) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val firstWarningAtMillis = quickAddFirstWarningAtMillis
        val recurrence = quickAddRecurrence
        if (recurrence != null && firstWarningAtMillis == null) {
            // The UI routes through the date picker first; this is a backstop.
            errorMessage = "Pick a start time for a repeating task."
            return
        }
        quickAddText = ""
        quickAddFirstWarningAtMillis = null
        quickAddRecurrence = null
        launchBusy {
            container.repository.addTask(trimmed, firstWarningAtMillis, recurrence)
            titleHistory = container.repository.titles()
        }
    }

    fun completeTask(id: String) {
        viewModelScope.launch {
            container.repository.completeTask(id)
        }
    }

    fun beginEditSchedule(task: OpenTaskEntity) {
        editingTask = task
    }

    fun saveSchedule(
        id: String,
        firstWarningAtMillis: Long?,
        repeatIntervalMinutes: Int,
        recurrence: Recurrence?,
    ) {
        editingTask = null
        launchBusy {
            container.repository.editSchedule(id, firstWarningAtMillis, repeatIntervalMinutes, recurrence)
        }
    }

    fun snoozeTask(id: String, minutes: Int) {
        viewModelScope.launch {
            container.repository.snoozeTask(id, minutes)
        }
    }

    fun saveSettings(
        initialDelayMinutes: Int,
        repeatIntervalMinutes: Int,
        mediumWaitMinutes: Int,
        longWaitMinutes: Int,
        onDone: () -> Unit,
    ) {
        launchBusy {
            container.repository.updateSettings(
                SettingsDto(initialDelayMinutes, repeatIntervalMinutes, mediumWaitMinutes, longWaitMinutes),
            )
            onDone()
        }
    }

    fun signOut() {
        launchBusy {
            container.repository.signOut()
            container.session.clear()
            quickAddText = ""
            quickAddFirstWarningAtMillis = null
            quickAddRecurrence = null
            editingTask = null
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
