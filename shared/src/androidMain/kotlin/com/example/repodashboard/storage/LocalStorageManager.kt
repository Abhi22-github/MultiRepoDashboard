package com.example.repodashboard.storage

import com.example.repodashboard.models.AppConfig
import com.example.repodashboard.models.ReleasePlan
import com.example.repodashboard.models.ReleasePlans

actual object LocalStorageManager {
    actual fun saveToken(token: String) {}
    actual fun getToken(): String? = null
    actual fun clearToken() {}
    actual fun saveReleasePlans(plans: ReleasePlans) {}
    actual fun getReleasePlans(): ReleasePlans = ReleasePlans()
    actual fun updateReleasePlan(repoName: String, plan: ReleasePlan) {}
    actual fun saveAppConfig(config: AppConfig) {}
    actual fun getAppConfig(): AppConfig? = null
    actual fun saveDarkMode(enabled: Boolean) {}
    actual fun getDarkMode(): Boolean = false
    actual fun savePinned(names: Set<String>) {}
    actual fun getPinned(): Set<String> = emptySet()
}
