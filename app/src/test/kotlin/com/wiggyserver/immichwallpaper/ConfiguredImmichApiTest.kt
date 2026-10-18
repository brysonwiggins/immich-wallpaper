package com.wiggyserver.immichwallpaper

import com.wiggyserver.immichwallpaper.core.ScreenKind
import com.wiggyserver.immichwallpaper.core.WallpaperFlags
import com.wiggyserver.immichwallpaper.data.ImmichClient
import com.wiggyserver.immichwallpaper.data.ImmichException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ConfiguredImmichApiTest {
    private var url = "https://photos.example"
    private var key: String? = "k1"
    private var builds = 0
    private val api = ConfiguredImmichApi({ url }, { key }) { u, k -> builds++; ImmichClient(u, k) }

    @Test(expected = ImmichException.Unauthorized::class)
    fun `no key means unauthorized`() {
        key = null
        api.client()
    }

    @Test(expected = ImmichException.Unauthorized::class)
    fun `no server means unauthorized`() {
        url = " "
        api.client()
    }

    @Test(expected = ImmichException.Unauthorized::class)
    fun `an invalid url means unauthorized`() {
        url = "photos.example"
        api.client()
    }

    @Test
    fun `the client is reused until the url or key changes`() {
        val first = api.client()
        assertSame(first, api.client())
        key = "k2"
        val second = api.client()
        assertNotSame(first, second)
        url = "https://other.example"
        assertNotSame(second, api.client())
        assertEquals(3, builds)
    }

    @Test
    fun `the active screen bit follows the screen only with per-screen flags`() {
        assertEquals(WallpaperFlags.COVER, AppGraph.activeScreenBit(ScreenKind.COVER, perScreenFlags = true))
        assertEquals(WallpaperFlags.INNER, AppGraph.activeScreenBit(ScreenKind.INNER, perScreenFlags = true))
        assertEquals(0, AppGraph.activeScreenBit(ScreenKind.PHONE, perScreenFlags = true))
        assertEquals(0, AppGraph.activeScreenBit(ScreenKind.INNER, perScreenFlags = false))
    }
}
