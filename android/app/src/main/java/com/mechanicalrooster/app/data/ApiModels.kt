package com.mechanicalrooster.app.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val idToken: String)

@Serializable
data class SettingsDto(val initialDelayMinutes: Int, val repeatIntervalMinutes: Int)

@Serializable
data class LoginResponse(
    val token: String,
    val email: String,
    val name: String? = null,
    val settings: SettingsDto,
)

@Serializable
data class CreateTaskRequest(val title: String)

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val createdAt: String,
    val completedAt: String? = null,
    val initialDelayMinutes: Int,
    val repeatIntervalMinutes: Int,
)
