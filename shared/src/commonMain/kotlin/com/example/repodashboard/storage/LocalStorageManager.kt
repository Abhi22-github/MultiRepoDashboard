package com.example.repodashboard.storage

import com.example.repodashboard.models.AppConfig
import com.example.repodashboard.models.ReleasePlan
import com.example.repodashboard.models.ReleasePlans

expect object LocalStorageManager {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
    fun saveReleasePlans(plans: ReleasePlans)
    fun getReleasePlans(): ReleasePlans
    fun updateReleasePlan(repoName: String, plan: ReleasePlan)
    fun saveAppConfig(config: AppConfig)
    fun getAppConfig(): AppConfig?
    fun saveDarkMode(enabled: Boolean)
    fun getDarkMode(): Boolean
    fun savePinned(names: Set<String>)
    fun getPinned(): Set<String>
}
