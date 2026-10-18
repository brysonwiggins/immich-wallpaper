package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.data.ConnectionResult
import com.wiggyserver.immichwallpaper.data.ImmichException

/** User-facing text for a connection test, or null when it succeeded. */
fun connectionMessage(result: ConnectionResult): String? = when (result) {
    is ConnectionResult.Ok -> null
    ConnectionResult.Unauthorized -> "The server rejected this API key."
    is ConnectionResult.MissingPermissions ->
        "This API key is missing permissions: ${result.permissions.joinToString()}. Edit the key in Immich's web app and tick them."
    is ConnectionResult.Unreachable -> "Can't reach the server. Check the address, and that this phone can reach it (VPN, Wi-Fi)."
    is ConnectionResult.Error -> "The server answered with an error (HTTP ${result.status})."
}

/** User-facing text for a failed background call; [what] completes "Couldn't …". */
internal fun errorMessage(e: Exception, what: String): String = when (e) {
    is ImmichException.Unreachable -> "Can't reach your Immich server."
    is ImmichException.Unauthorized -> e.missingPermission?.let { "The API key is missing the $it permission. Edit it in Immich's web app." }
        ?: "The server rejected the API key. Check it in Settings."
    is ImmichException.Http -> "Couldn't $what (HTTP ${e.status})."
    else -> "Couldn't $what (${e.javaClass.simpleName})."
}

internal fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
