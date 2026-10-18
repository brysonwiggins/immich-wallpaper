package com.wiggyserver.immichwallpaper.core

/** Persists the offline-set position (DataStore in the app). */
interface IndexStore {
    fun get(): Int
    fun set(index: Int)
}

/**
 * Rotates through the owner's offline set **in order**, wrapping around, skipping items whose
 * files are missing ([isAvailable]). The position survives restarts via [store].
 */
class FallbackRotator(
    private val items: List<String>,
    private val store: IndexStore,
    private val isAvailable: (String) -> Boolean,
) {
    fun next(): String? {
        if (items.isEmpty()) return null
        val start = store.get().takeIf { it in items.indices } ?: 0
        for (step in items.indices) {
            val i = (start + step) % items.size
            if (isAvailable(items[i])) {
                store.set((i + 1) % items.size)
                return items[i]
            }
        }
        return null
    }
}
