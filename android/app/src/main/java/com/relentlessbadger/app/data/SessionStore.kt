package com.relentlessbadger.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

data class Session(
    val baseUrl: String,
    val token: String?,
    val email: String?,
    val initialDelayMinutes: Int,
    val repeatIntervalMinutes: Int,
    val mediumWaitMinutes: Int,
    val longWaitMinutes: Int,
) {
    val isSignedIn: Boolean get() = token != null && baseUrl.isNotBlank()
}

/**
 * The slice of session state the business logic needs: the current settings
 * snapshot and the "settings edited locally, not yet pushed" flag. Fakeable
 * in scenario tests without dragging DataStore in.
 */
interface SettingsStore {
    suspend fun current(): Session
    suspend fun saveSettings(settings: SettingsDto)
    suspend fun markSettingsDirty()
    suspend fun clearSettingsDirty()
    suspend fun isSettingsDirty(): Boolean
}

class SessionStore(private val context: Context) : SettingsStore {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val EMAIL = stringPreferencesKey("email")
        val INITIAL_DELAY = intPreferencesKey("initial_delay_minutes")
        val REPEAT_INTERVAL = intPreferencesKey("repeat_interval_minutes")
        val MEDIUM_WAIT = intPreferencesKey("medium_wait_minutes")
        val LONG_WAIT = intPreferencesKey("long_wait_minutes")
        val SETTINGS_DIRTY = booleanPreferencesKey("settings_dirty")
    }

    // Mirrors kept warm for the OkHttp auth interceptor, which cannot suspend.
    @Volatile var cachedToken: String? = null
        private set
    @Volatile var cachedBaseUrl: String = ""
        private set

    val sessionFlow: Flow<Session> = context.dataStore.data.map { prefs ->
        Session(
            baseUrl = prefs[Keys.BASE_URL] ?: "",
            token = prefs[Keys.TOKEN],
            email = prefs[Keys.EMAIL],
            initialDelayMinutes = prefs[Keys.INITIAL_DELAY] ?: 60,
            repeatIntervalMinutes = prefs[Keys.REPEAT_INTERVAL] ?: 15,
            mediumWaitMinutes = prefs[Keys.MEDIUM_WAIT] ?: 60,
            longWaitMinutes = prefs[Keys.LONG_WAIT] ?: 240,
        ).also {
            cachedToken = it.token
            cachedBaseUrl = it.baseUrl
        }
    }

    override suspend fun current(): Session = sessionFlow.first()

    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = baseUrl.trim().trimEnd('/') }
    }

    suspend fun saveLogin(token: String, email: String, settings: SettingsDto) {
        context.dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.EMAIL] = email
            it[Keys.INITIAL_DELAY] = settings.initialDelayMinutes
            it[Keys.REPEAT_INTERVAL] = settings.repeatIntervalMinutes
            it[Keys.MEDIUM_WAIT] = settings.mediumWaitMinutes
            it[Keys.LONG_WAIT] = settings.longWaitMinutes
        }
        cachedToken = token
    }

    override suspend fun saveSettings(settings: SettingsDto) {
        context.dataStore.edit {
            it[Keys.INITIAL_DELAY] = settings.initialDelayMinutes
            it[Keys.REPEAT_INTERVAL] = settings.repeatIntervalMinutes
            it[Keys.MEDIUM_WAIT] = settings.mediumWaitMinutes
            it[Keys.LONG_WAIT] = settings.longWaitMinutes
        }
    }

    override suspend fun markSettingsDirty() {
        context.dataStore.edit { it[Keys.SETTINGS_DIRTY] = true }
    }

    override suspend fun clearSettingsDirty() {
        context.dataStore.edit { it[Keys.SETTINGS_DIRTY] = false }
    }

    override suspend fun isSettingsDirty(): Boolean =
        context.dataStore.data.first()[Keys.SETTINGS_DIRTY] ?: false

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
        cachedToken = null
        cachedBaseUrl = ""
    }
}
