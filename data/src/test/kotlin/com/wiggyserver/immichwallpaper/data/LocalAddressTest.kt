package com.wiggyserver.immichwallpaper.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException

class LocalAddressTest {
    private fun ip(s: String) = InetAddress.getByName(s)
    private val dns = mapOf("nas.lan" to listOf(ip("192.168.1.20")), "photos.example.com" to listOf(ip("93.184.216.34")))
    private fun local(url: String) = LocalAddress.isLocalServer(url) { host -> dns[host] ?: throw UnknownHostException(host) }

    @Test
    fun `private, link-local and loopback addresses are local`() {
        listOf("http://192.168.0.7:2283", "http://10.0.0.5", "https://172.20.1.1/immich", "http://169.254.3.4", "http://127.0.0.1:2283", "http://[fd12::1]:2283", "http://[fe80::1]")
            .forEach { assertTrue(local(it), it) }
    }

    @Test
    fun `public addresses are not`() {
        listOf("https://93.184.216.34", "http://172.32.0.1", "https://[2001:db8::1]").forEach { assertFalse(local(it), it) }
    }

    @Test
    fun `host names are resolved, mDNS names are local`() {
        assertTrue(local("http://nas.lan:2283"))
        assertFalse(local("https://photos.example.com"))
        assertTrue(local("http://immich.local:2283"))
    }

    @Test
    fun `unresolvable or invalid addresses are not treated as local`() {
        assertFalse(local("https://nowhere.invalid"))
        assertFalse(local("not a url"))
    }
}
