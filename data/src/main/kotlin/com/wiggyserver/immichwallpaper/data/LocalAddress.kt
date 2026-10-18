package com.wiggyserver.immichwallpaper.data

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Whether a server is on the local network. Android 17 blocks apps that target it from reaching
 * those addresses without the ACCESS_LOCAL_NETWORK runtime permission ("Nearby devices").
 */
object LocalAddress {
    /** Blocking (resolves DNS). Unknown hosts and bad URLs count as not local. */
    fun isLocalServer(url: String, resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }): Boolean {
        val host = runCatching { URI(url.trim()).host }.getOrNull()?.removeSurrounding("[", "]") ?: return false
        if (host.endsWith(".local", ignoreCase = true)) return true
        // IP literals are parsed without a lookup; names go through DNS.
        val literal = host.contains(':') || host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
        val addresses = runCatching { if (literal) listOf(InetAddress.getByName(host)) else resolve(host) }.getOrDefault(emptyList())
        return addresses.any(::isLocal)
    }

    fun isLocal(a: InetAddress): Boolean =
        a.isSiteLocalAddress || a.isLinkLocalAddress || a.isLoopbackAddress ||
            (a is Inet6Address && (a.address[0].toInt() and 0xFE) == 0xFC) // IPv6 unique local, fc00::/7
}
