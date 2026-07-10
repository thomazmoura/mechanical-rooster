package com.relentlessbadger.app.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val idToken: String)

@Serializable
data class SettingsDto(
    val initialDelayMinutes: Int,
    val repeatIntervalMinutes: Int,
    val mediumWaitMinutes: Int = 60,
    val longWaitMinutes: Int = 240,
)

@Serializable
data class LoginResponse(
    val token: String,
    val email: String,
    val name: String? = null,
    val settings: SettingsDto,
)

@Serializable
data class CreateTaskRequest(
    val title: String,
    val firstWarningAt: String? = null,
    // Set when pushing an offline-created task: the client-minted id makes the
    // push idempotent, the rest preserves the original creation time and the
    // settings snapshot the task was created under.
    val id: String? = null,
    val createdAt: String? = null,
    val initialDelayMinutes: Int? = null,
    val repeatIntervalMinutes: Int? = null,
)

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val createdAt: String,
    val completedAt: String? = null,
    val initialDelayMinutes: Int,
    val repeatIntervalMinutes: Int,
    val firstWarningAt: String? = null,
)
