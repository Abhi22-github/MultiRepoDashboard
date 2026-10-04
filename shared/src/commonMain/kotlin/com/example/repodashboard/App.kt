package com.example.repodashboard

import androidx.compose.runtime.*
import com.example.repodashboard.github.GitHubClient
import com.example.repodashboard.models.*
import com.example.repodashboard.storage.LocalStorageManager
import com.example.repodashboard.ui.screens.DashboardScreen
import com.example.repodashboard.ui.screens.RepoSelectionScreen
import com.example.repodashboard.ui.screens.TokenInputScreen
import com.example.repodashboard.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun App() {
    var darkMode by remember { mutableStateOf(LocalStorageManager.getDarkMode()) }
    var pinned by remember { mutableStateOf(LocalStorageManager.getPinned()) }
    AppTheme(darkTheme = darkMode) {
        var appState by remember { mutableStateOf(AppState()) }
        var savedConfig by remember { mutableStateOf<AppConfig?>(null) }
        var gitHubClient by remember { mutableStateOf<GitHubClient?>(null) }
        var isValidatingToken by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            val token = LocalStorageManager.getToken() ?: return@LaunchedEffect
            val apiBase = LocalStorageManager.getApiBase() ?: "https://api.github.com"
            val client = GitHubClient(token, apiBase)
            if (client.validateToken() == null) {
                gitHubClient = client
                appState = appState.copy(githubToken = token)
                val config = LocalStorageManager.getAppConfig()
                if (config == null || config.repositories.isEmpty()) {
                    appState = appState.copy(showRepoSelection = true)
                } else {
                    savedConfig = config
                    loadRepositories(appState, config, client) { appState = it }
                }
            } else {
                LocalStorageManager.clearToken()
                client.close()
            }
        }

        when {
            appState.githubToken == null -> {
                TokenInputScreen(
                    isLoading = isValidatingToken,
                    error = appState.error,
                    onTokenSaved = { token, host ->
                        scope.launch {
                            isValidatingToken = true
                            appState = appState.copy(error = null)
                            val apiBase = normalizeApiBase(host)
                            val client = GitHubClient(token, apiBase)
                            val validationError = client.validateToken()
                            if (validationError == null) {
                                LocalStorageManager.saveToken(token)
                                LocalStorageManager.saveApiBase(apiBase)
                                gitHubClient = client
                                appState = appState.copy(githubToken = token)
                                val config = LocalStorageManager.getAppConfig()
                                if (config == null || config.repositories.isEmpty()) {
                                    appState = appState.copy(showRepoSelection = true)
                                } else {
                                    savedConfig = config
                                    loadRepositories(appState.copy(githubToken = token), config, client) { appState = it }
                                }
                            } else {
                                client.close()
                                appState = appState.copy(error = validationError)
                            }
                            isValidatingToken = false
                        }
                    }
                )
            }

            appState.showRepoSelection -> {
                RepoSelectionScreen(
                    client = gitHubClient!!,
                    existingConfig = savedConfig ?: AppConfig(emptyList()),
                    onSave = { newConfig ->
                        LocalStorageManager.saveAppConfig(newConfig)
                        savedConfig = newConfig
                        appState = appState.copy(showRepoSelection = false)
                        scope.launch {
                            loadRepositories(
                                appState.copy(showRepoSelection = false),
                                newConfig,
                                gitHubClient!!
                            ) { appState = it }
                        }
                    },
                    onBack = if (savedConfig?.repositories?.isNotEmpty() == true) {
                        { appState = appState.copy(showRepoSelection = false) }
                    } else null
                )
            }

            else -> {
                DashboardScreen(
                    state = appState,
                    client = gitHubClient,
                    pinned = pinned,
                    onTogglePin = { name ->
                        pinned = if (name in pinned) pinned - name else pinned + name
                        LocalStorageManager.savePinned(pinned)
                    },
                    onRefresh = {
                        scope.launch {
                            val client = gitHubClient
                            val config = savedConfig
                            if (client != null && config != null) {
                                loadRepositories(appState, config, client) { appState = it }
                            }
                        }
                    },
                    onRepoSelected = { repoName -> appState = appState.copy(selectedRepoName = repoName) },
                    onDashboardSelected = { appState = appState.copy(selectedRepoName = null) },
                    onManageRepos = { appState = appState.copy(showRepoSelection = true) },
                    onClearToken = {
                        LocalStorageManager.clearToken()
                        gitHubClient?.close()
                        gitHubClient = null
                        savedConfig = null
                        appState = AppState()
                    },
                    isDark = darkMode,
                    onToggleTheme = {
                        darkMode = !darkMode
                        LocalStorageManager.saveDarkMode(darkMode)
                    }
                )
            }
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

private suspend fun loadRepositories(
    currentState: AppState,
    config: AppConfig,
    client: GitHubClient,
    updateState: (AppState) -> Unit
) {
    val state = currentState.copy(isLoading = true, error = null)
    updateState(state)

    val repos = mutableListOf<Repository>()
    for (repoConfig in config.repositories) {
        val openPRs = client.getOpenPRs(repoConfig.owner, repoConfig.repo).getOrElse { emptyList() }
        val version = client.getLatestRelease(repoConfig.owner, repoConfig.repo)
            .getOrNull()?.tag_name?.removePrefix("v") ?: ""
        repos.add(
            Repository(
                name = repoConfig.name,
                owner = repoConfig.owner,
                repo = repoConfig.repo,
                description = repoConfig.description,
                version = version,
                defaultBranch = repoConfig.defaultBranch,
                openPRsCount = openPRs.size,
                openPRs = openPRs
            )
        )
    }

    updateState(state.copy(isLoading = false, repositories = repos))
}
