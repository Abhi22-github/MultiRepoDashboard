package com.example.repodashboard.config

import com.example.repodashboard.models.AppConfig
import kotlinx.serialization.json.Json
import repodashboard.shared.generated.resources.Res

private val json = Json { ignoreUnknownKeys = true }

suspend fun loadConfig(): AppConfig {
    return try {
        val bytes = Res.readBytes("files/config.json")
        json.decodeFromString(bytes.decodeToString())
    } catch (e: Exception) {
        AppConfig(emptyList())
    }
}
