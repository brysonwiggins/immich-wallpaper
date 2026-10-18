package com.wiggyserver.immichwallpaper.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface Destination {
    data object Home : Destination
    data object OfflineSet : Destination
    data object Sources : Destination
    data object Settings : Destination
    data object Server : Destination
    data object Log : Destination
    data object About : Destination

    /** Pick photos for the offline set (with [slots]: replace those), or photos to hide. */
    data class Picker(val slots: List<Int> = emptyList(), val hide: Boolean = false) : Destination
}

enum class Tab(val root: Destination) {
    HOME(Destination.Home), OFFLINE_SET(Destination.OfflineSet), SOURCES(Destination.Sources), SETTINGS(Destination.Settings)
}

/** Tabs with a back stack each time; back from another tab returns Home (Android's back convention). */
class Navigator {
    private val _stack = MutableStateFlow<List<Destination>>(listOf(Destination.Home))
    val stack: StateFlow<List<Destination>> = _stack.asStateFlow()

    val current: Destination get() = _stack.value.last()
    val tab: Tab get() = Tab.entries.first { it.root == _stack.value.first() }

    fun select(tab: Tab) { _stack.value = listOf(tab.root) }
    fun push(destination: Destination) { _stack.value = _stack.value + destination }

    fun back(): Boolean {
        val s = _stack.value
        return when {
            s.size > 1 -> { _stack.value = s.dropLast(1); true }
            s.first() != Destination.Home -> { _stack.value = listOf(Destination.Home); true }
            else -> false
        }
    }
}
