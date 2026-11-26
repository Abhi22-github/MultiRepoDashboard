package com.example.repodashboard

import androidx.compose.runtime.*
import com.example.repodashboard.data.*
import com.example.repodashboard.github.GitHubClient
import com.example.repodashboard.models.AppConfig
import com.example.repodashboard.models.RateLimitResponse
import com.example.repodashboard.storage.LocalStorageManager
import com.example.repodashboard.ui.inbox.InboxScreen
import com.example.repodashboard.ui.screens.RepoSelectionScreen
import com.example.repodashboard.ui.screens.TokenInputScreen
import com.example.repodashboard.ui.theme.AppTheme
import kotlinx.coroutines.launch

private enum class Screen { Connect, Repos, Inbox }

@Composable
fun App() {
    var darkMode by remember { mutableStateOf(LocalStorageManager.getDarkMode()) }
    var pinned by remember { mutableStateOf(LocalStorageManager.getPinned()) }
    AppTheme(darkTheme = darkMode) {
        var screen by remember { mutableStateOf(if (LocalStorageManager.getToken() != null) Screen.Inbox else Screen.Connect) }
        var client by remember { mutableStateOf<GitHubClient?>(null) }
        var apiBase by remember { mutableStateOf(LocalStorageManager.getApiBase() ?: "https://api.github.com") }
        var config by remember { mutableStateOf(LocalStorageManager.getAppConfig()) }
        var inbox by remember { mutableStateOf<InboxData?>(null) }
        var loading by remember { mutableStateOf(false) }
        var validating by remember { mutableStateOf(false) }
        var connectError by remember { mutableStateOf<String?>(null) }
        var apiMode by remember { mutableStateOf(LocalStorageManager.getApiMode()?.let { m -> ApiMode.entries.firstOrNull { it.name == m } } ?: ApiMode.Rest) }
        var rate by remember { mutableStateOf<RateLimitResponse?>(null) }

        val scope = rememberCoroutineScope()
        fun loadRate() {
            val c = client ?: return
            scope.launch { c.getRateLimit().onSuccess { rate = it } }
        }

        fun refresh() {
            val c = client ?: return
            val cfg = config ?: return
            if (loading) return
            scope.launch {
                loading = true
                val fresh = loadInbox(c, cfg, apiBase, apiMode)
                if (fresh.problem == null) LocalStorageManager.saveInboxCache(encodeInboxCache(fresh, cfg, apiBase))
                // On a failed sync keep the last good data and show the problem on top of it.
                val prev = inbox
                inbox = if (fresh.problem != null && fresh.repos.isEmpty() && prev != null && prev.repos.isNotEmpty())
                    prev.copy(problem = fresh.problem, rateResetAt = fresh.rateResetAt)
                else fresh
                loading = false
                c.getRateLimit().onSuccess { rate = it }
            }
        }

        fun openInbox(c: GitHubClient) {
            client = c
            val cfg = config
            if (cfg == null || cfg.repositories.isEmpty()) { screen = Screen.Repos; return }
            screen = Screen.Inbox
            // Show the last load straight away; only hit GitHub if it's stale.
            val cached = decodeInboxCache(LocalStorageManager.getInboxCache(), cfg, apiBase)
            inbox = cached
            val age = cached?.syncedAt?.let { nowMs() - it }
            if (age == null || age > CACHE_FRESH_MS) refresh()
        }

        fun signOut() {
            LocalStorageManager.clearToken()
            LocalStorageManager.saveInboxCache(null)
            client?.close()
            client = null
            inbox = null
            connectError = null
            screen = Screen.Connect
        }

        // Restore a saved session. A token that no longer works sends the user back to Connect.
        LaunchedEffect(Unit) {
            val token = LocalStorageManager.getToken() ?: return@LaunchedEffect
            val c = GitHubClient(token, apiBase)
            loading = true
            val err = c.validateToken()
            loading = false
            if (err == null) openInbox(c) else { c.close(); LocalStorageManager.clearToken(); connectError = err; screen = Screen.Connect }
        }

        when (screen) {
            Screen.Connect -> TokenInputScreen(
                isLoading = validating,
                error = connectError,
                onTokenSaved = { token, host ->
                    scope.launch {
                        validating = true
                        connectError = null
                        val base = normalizeApiBase(host)
                        val c = GitHubClient(token, base)
                        val err = c.validateToken()
                        if (err == null) {
                            LocalStorageManager.saveToken(token)
                            LocalStorageManager.saveApiBase(base)
                            apiBase = base
                            openInbox(c)
                        } else {
                            c.close()
                            connectError = err
                        }
                        validating = false
                    }
                }
            )

            Screen.Repos -> client?.let { c ->
                RepoSelectionScreen(
                    client = c,
                    existingConfig = config ?: AppConfig(emptyList()),
                    onSave = { newConfig ->
                        LocalStorageManager.saveAppConfig(newConfig)
                        config = newConfig
                        screen = Screen.Inbox
                        inbox = null
                        refresh()
                    },
                    onBack = if (config?.repositories?.isNotEmpty() == true) ({ screen = Screen.Inbox }) else null
                )
            }

            Screen.Inbox -> InboxScreen(
                data = inbox,
                loading = loading || client == null,
                noRepos = client != null && config?.repositories.isNullOrEmpty(),
                client = client,
                apiBase = apiBase,
                pinned = pinned,
                onTogglePin = { name ->
                    pinned = if (name in pinned) pinned - name else pinned + name
                    LocalStorageManager.savePinned(pinned)
                },
                onRefresh = ::refresh,
                onManageRepos = { if (client != null) screen = Screen.Repos },
                onSignOut = ::signOut,
                isDark = darkMode,
                onToggleTheme = {
                    darkMode = !darkMode
                    LocalStorageManager.saveDarkMode(darkMode)
                },
                apiMode = apiMode,
                onApiMode = { m ->
                    apiMode = m
                    LocalStorageManager.saveApiMode(m.name)
                    refresh()
                },
                rate = rate,
                onLoadRate = ::loadRate,
            )
        }
    }
}

/**
 * Turns a user-entered GitHub host into the correct REST API base URL.
 *  - blank                     -> https://api.github.com          (public GitHub / Enterprise Cloud)
 *  - github.mycompany.com      -> https://github.mycompany.com/api/v3  (Enterprise Server)
 *  - mycompany.ghe.com         -> https://api.mycompany.ghe.com   (Enterprise Cloud, data residency)
 */
private fun normalizeApiBase(input: String): String {
    var s = input.trim().removePrefix("https://").removePrefix("http://")
    s = s.substringBefore("/").trim()
    return when {
        s.isEmpty() || s == "github.com" || s == "api.github.com" -> "https://api.github.com"
        s.endsWith(".ghe.com") -> if (s.startsWith("api.")) "https://$s" else "https://api.$s"
        else -> "https://$s/api/v3"
    }
}
