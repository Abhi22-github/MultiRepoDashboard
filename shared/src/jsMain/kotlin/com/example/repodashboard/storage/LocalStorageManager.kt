package com.example.repodashboard.storage

import com.example.repodashboard.models.AppConfig
import com.example.repodashboard.models.ReleasePlan
import com.example.repodashboard.models.ReleasePlans
import kotlinx.browser.localStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private const val TOKEN_KEY = "release-tool:github-token"
private const val PLANS_KEY = "release-tool:release-plans"
private const val CONFIG_KEY = "release-tool:app-config"
private const val THEME_KEY = "release-tool:dark-mode"
private const val PINNED_KEY = "release-tool:pinned"

actual object LocalStorageManager {
    actual fun saveToken(token: String) {
        localStorage.setItem(TOKEN_KEY, token)
    }

    actual fun getToken(): String? = localStorage.getItem(TOKEN_KEY)

    actual fun clearToken() {
        localStorage.removeItem(TOKEN_KEY)
    }

    actual fun saveReleasePlans(plans: ReleasePlans) {
        localStorage.setItem(PLANS_KEY, json.encodeToString(plans))
    }

    actual fun getReleasePlans(): ReleasePlans {
        val raw = localStorage.getItem(PLANS_KEY) ?: return ReleasePlans()
        return try { json.decodeFromString(raw) } catch (e: Exception) { ReleasePlans() }
    }

    actual fun updateReleasePlan(repoName: String, plan: ReleasePlan) {
        val current = getReleasePlans()
        saveReleasePlans(current.copy(
            plans = current.plans + (repoName to plan),
            lastUpdated = js("new Date().toISOString()").toString()
        ))
    }

    actual fun saveAppConfig(config: AppConfig) {
        localStorage.setItem(CONFIG_KEY, json.encodeToString(config))
    }

    actual fun getAppConfig(): AppConfig? {
        val raw = localStorage.getItem(CONFIG_KEY) ?: return null
        return try { json.decodeFromString(raw) } catch (e: Exception) { null }
    }

    actual fun saveDarkMode(enabled: Boolean) {
        localStorage.setItem(THEME_KEY, if (enabled) "1" else "0")
    }

    actual fun getDarkMode(): Boolean = localStorage.getItem(THEME_KEY) == "1"

    actual fun savePinned(names: Set<String>) {
        localStorage.setItem(PINNED_KEY, names.joinToString(","))
    }

    actual fun getPinned(): Set<String> =
        localStorage.getItem(PINNED_KEY)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
}
