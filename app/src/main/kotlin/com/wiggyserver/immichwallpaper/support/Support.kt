package com.wiggyserver.immichwallpaper.support

import androidx.compose.runtime.Composable

/**
 * The "Support development" part of About. Each build flavor provides `createSupport`:
 * foss links to donation pages; play offers a Google Play Billing tip jar (Play's payment rules).
 */
interface Support {
    @Composable
    fun Section()

    /** Releases resources (the billing connection) when the screen closes. */
    fun close() {}
}
