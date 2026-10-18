package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.data.OfflineSetRepository
import com.wiggyserver.immichwallpaper.data.OfflineSlots
import com.wiggyserver.immichwallpaper.data.SlotResult
import com.wiggyserver.immichwallpaper.data.SlotStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One of the offline photos; [assetId] null means the slot is empty. */
data class SlotView(val index: Int, val assetId: String?, val status: SlotStatus?)

data class OfflineSetState(
    val slots: List<SlotView> = List(SLOT_COUNT) { SlotView(it, null, null) },
    val busy: Boolean = false,
    val message: String? = null,
    /** Slots chosen for a group action (Replace, Remove, Replace randomly); only filled slots. */
    val selected: Set<Int> = emptySet(),
) {
    val free: Int get() = slots.count { it.assetId == null }
    val ready: Int get() = slots.count { it.status == SlotStatus.READY }
    val chosenIds: Set<String> get() = slots.mapNotNull { it.assetId }.toSet()
}

private const val SLOT_COUNT = OfflineSetRepository.MAX_SLOTS
private const val RANDOM_BATCH = 25
private const val MAX_RANDOM = 100

/** The hand-picked photos shown when Immich can't be reached. Each change downloads and renders at once. */
class OfflineSetModel(
    private val app: AppPort,
    private val slots: OfflineSlots,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(OfflineSetState())
    val state: StateFlow<OfflineSetState> = _state.asStateFlow()

    fun refresh() {
        scope.launch { publish(withContext(io) { slots.slots() }) }
    }

    /** Puts picked photos into the empty slots, in slot order. */
    fun add(assetIds: List<String>) = run("add photos") {
        val free = (0 until SLOT_COUNT) - slots.slots().map { it.index }.toSet()
        free.zip(assetIds).map { (i, id) -> slots.setSlot(i, id) }
    }

    // Photos replaced away by "Replace randomly" in this session, so cycling never shows them again
    // until the sources run out of new ones.
    private val rejected = mutableSetOf<String>()

    fun replace(index: Int, assetId: String) = replace(listOf(index), listOf(assetId))

    /** Picked photos go into [indexes], in order. */
    fun replace(indexes: List<Int>, assetIds: List<String>) = run("replace the photos") {
        indexes.zip(assetIds).map { (i, id) -> slots.setSlot(i, id) }
    }

    fun remove(index: Int) = remove(listOf(index))

    fun remove(indexes: List<Int>) = run("remove the photos") { indexes.forEach { slots.clearSlot(it) }; emptyList() }

    /**
     * New random photos from the sources for [indexes]: never one already in the set, nor one replaced
     * earlier, so tapping again cycles through new photos. Once none are left, earlier ones come back.
     */
    fun replaceRandomly(indexes: List<Int>) = run("pick random photos") {
        val current = slots.slots().associate { it.index to it.assetId }
        val inSet = current.values.toSet()
        val leaving = indexes.mapNotNull { current[it] }
        val count = (indexes.size + inSet.size + rejected.size).coerceIn(RANDOM_BATCH, MAX_RANDOM)
        val offered = app.randomPhotoIds(count).distinct()
        var picks = offered.filter { it !in inSet && it !in rejected }
        if (picks.size < indexes.size) {
            // Every photo has been tried: start the cycle again (still never one that's in the set).
            rejected.clear()
            picks = offered.filter { it !in inSet }
        }
        if (picks.isEmpty()) throw NothingNew()
        rejected += leaving
        indexes.zip(picks).map { (i, id) -> slots.setSlot(i, id) }
    }

    fun toggleSelected(index: Int) = _state.update { s ->
        when {
            index in s.selected -> s.copy(selected = s.selected - index)
            s.slots.getOrNull(index)?.assetId != null -> s.copy(selected = s.selected + index)
            else -> s
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    private class NothingNew : Exception()

    fun fillRandomly() = run("pick random photos") {
        val current = slots.slots()
        val free = (0 until SLOT_COUNT) - current.map { it.index }.toSet()
        val chosen = current.map { it.assetId }.toSet()
        val picks = app.randomPhotoIds(free.size * 2).filter { it !in chosen }.distinct()
        free.zip(picks).map { (i, id) -> slots.setSlot(i, id) }
    }

    private fun run(what: String, block: () -> List<SlotResult>) {
        _state.update { it.copy(busy = true, message = null, selected = emptySet()) }
        scope.launch {
            val message = try {
                val results = withContext(io) { block() }
                summarize(results)
            } catch (e: NothingNew) {
                "Your sources have no other photos."
            } catch (e: Exception) {
                errorMessage(e, what)
            }
            publish(withContext(io) { slots.slots() }, message)
        }
    }

    private fun summarize(results: List<SlotResult>): String? {
        val pending = results.count { it == SlotResult.Pending }
        val missing = results.count { it == SlotResult.NotFound }
        return listOfNotNull(
            if (pending > 0) "${plural(pending, "photo")} will download when the server is reachable." else null,
            if (missing > 0) "${plural(missing, "photo")} no longer exists on the server." else null,
        ).joinToString(" ").ifEmpty { null }
    }

    private fun publish(current: List<com.wiggyserver.immichwallpaper.data.OfflineSlot>, message: String? = _state.value.message) {
        val byIndex = current.associateBy { it.index }
        _state.value = OfflineSetState(
            slots = List(SLOT_COUNT) { i -> byIndex[i].let { SlotView(i, it?.assetId, it?.status) } },
            busy = false,
            message = message,
            selected = _state.value.selected.filterTo(mutableSetOf()) { it in byIndex },
        )
    }
}
