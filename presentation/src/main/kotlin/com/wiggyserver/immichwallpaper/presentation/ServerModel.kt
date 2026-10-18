package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.data.ConnectionResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ServerStatus {
    data object Idle : ServerStatus
    data object Testing : ServerStatus
    data class Connected(val version: String) : ServerStatus

    /** Android 17+ needs the "Nearby devices" permission to reach a server on the home network. */
    data object NeedsLocalNetwork : ServerStatus
    data class Failed(val message: String) : ServerStatus
}

data class ServerState(val url: String, val key: String, val hasSavedKey: Boolean, val status: ServerStatus)

/** Server address and API key: checked against the server before anything is saved. */
class ServerModel(private val app: AppPort, private val scope: CoroutineScope, private val io: CoroutineDispatcher) {
    private val _state = MutableStateFlow(ServerState(app.config.value.serverUrl, "", app.hasApiKey(), ServerStatus.Idle))
    val state: StateFlow<ServerState> = _state.asStateFlow()

    fun setUrl(url: String) = _state.update { it.copy(url = url, status = ServerStatus.Idle) }
    fun setKey(key: String) = _state.update { it.copy(key = key, status = ServerStatus.Idle) }

    fun test() {
        val s = _state.value
        val url = s.url.trim()
        val key = s.key.trim().ifEmpty { null }
        val invalid = when {
            !url.startsWith("https://") && !url.startsWith("http://") -> "Start the address with https:// or http://"
            key == null && !s.hasSavedKey -> "Enter your Immich API key."
            else -> null
        }
        if (invalid != null) {
            _state.update { it.copy(status = ServerStatus.Failed(invalid)) }
            return
        }
        _state.update { it.copy(status = ServerStatus.Testing) }
        scope.launch {
            val blocked = withContext(io) { !app.localNetworkAllowed() && app.isLocalServer(url) }
            if (blocked) {
                _state.update { it.copy(status = ServerStatus.NeedsLocalNetwork) }
                return@launch
            }
            val result = withContext(io) { app.testConnection(url, key) }
            if (result is ConnectionResult.Unreachable && !app.localNetworkAllowed()) {
                // The name may resolve to a home-network address only from the phone's network.
                _state.update { it.copy(status = ServerStatus.NeedsLocalNetwork) }
            } else if (result is ConnectionResult.Ok) {
                withContext(io) { app.saveServer(url, key) }
                _state.update { it.copy(url = url, key = "", hasSavedKey = true, status = ServerStatus.Connected(result.serverVersion)) }
            } else {
                _state.update { it.copy(status = ServerStatus.Failed(connectionMessage(result)!!)) }
            }
        }
    }

    /** Called with the answer to the system's "Nearby devices" prompt. */
    fun localNetworkResult(granted: Boolean) {
        if (granted) {
            test()
        } else {
            _state.update {
                it.copy(
                    status = ServerStatus.Failed(
                        "Without the Nearby devices permission, Android blocks servers on your home network. " +
                            "Allow it in Settings → Apps → Wallpapers with Immich → Permissions.",
                    ),
                )
            }
        }
    }
}
