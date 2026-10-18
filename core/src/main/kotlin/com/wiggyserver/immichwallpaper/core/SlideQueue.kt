package com.wiggyserver.immichwallpaper.core

/** Bounded FIFO of pre-rendered slides. [deficit] is how many the background refill should add. */
class SlideQueue<T>(val target: Int = 3) {
    private val items = ArrayDeque<T>()

    val size: Int @Synchronized get() = items.size
    val deficit: Int @Synchronized get() = (target - items.size).coerceAtLeast(0)
    val isFull: Boolean @Synchronized get() = items.size >= target

    /** Adds [item] unless the queue is already at its target size. */
    @Synchronized
    fun offer(item: T): Boolean {
        if (items.size >= target) return false
        items.addLast(item)
        return true
    }

    @Synchronized fun poll(): T? = items.removeFirstOrNull()

    @Synchronized fun peek(): T? = items.firstOrNull()

    @Synchronized fun snapshot(): List<T> = items.toList()

    /** Empties the queue (e.g. settings changed) and returns the dropped slides so their files can be deleted. */
    @Synchronized
    fun clear(): List<T> = items.toList().also { items.clear() }

    /** Removes the slides matching [predicate], keeping the order of the rest; returns the removed ones. */
    @Synchronized
    fun removeIf(predicate: (T) -> Boolean): List<T> = items.filter(predicate).also { removed -> items.removeAll(removed.toSet()) }
}
