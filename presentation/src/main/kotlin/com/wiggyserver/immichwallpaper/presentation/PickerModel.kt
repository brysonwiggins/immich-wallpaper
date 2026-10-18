package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.data.PhotoLibrary
import com.wiggyserver.immichwallpaper.data.SourceFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the picker browses from. */
sealed interface PickerSource {
    val filter: SourceFilter

    data object All : PickerSource { override val filter = SourceFilter() }
    data object Favorites : PickerSource { override val filter = SourceFilter(favoritesOnly = true) }

    /** Photos in no album at all; the server returns all photos and album members are dropped on the phone. */
    data object NotInAlbum : PickerSource { override val filter = SourceFilter() }
    data class Album(val id: String, val name: String) : PickerSource { override val filter get() = SourceFilter(albumIds = listOf(id)) }
    data class Person(val id: String, val name: String) : PickerSource { override val filter get() = SourceFilter(personIds = listOf(id)) }
}

data class PickerState(
    val sources: List<PickerSource> = listOf(PickerSource.All, PickerSource.Favorites),
    val source: PickerSource = PickerSource.All,
    val photos: List<Asset> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val selected: List<String> = emptyList(),
    val maxSelection: Int = 1,
    val alreadyChosen: Set<String> = emptySet(),
) {
    val canSelectMore: Boolean get() = selected.size < maxSelection
}

/**
 * Browses the library newest first, a page at a time, and collects up to [maxSelection] photos.
 *
 * With a [pool] it browses only the photos that can become wallpapers ("Included photos"): one chip
 * per chosen album or person, and on the phone it applies the same rules as the background job:
 * in a chosen album AND showing a chosen person (when both are chosen), minus hidden photos, people
 * and albums. Single-source queries keep the server side simple and verified.
 */
class PickerModel(
    private val library: PhotoLibrary,
    maxSelection: Int,
    alreadyChosen: Set<String>,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val pool: SourceFilter? = null,
) {
    private val narrowed = pool != null && (pool.albumIds.isNotEmpty() || pool.personIds.isNotEmpty() || pool.notInAlbum)

    private val _state = MutableStateFlow(
        PickerState(maxSelection = maxSelection, alreadyChosen = alreadyChosen).let { s ->
            if (!narrowed) s else {
                val chips = pool!!.albumIds.map { PickerSource.Album(it, "") } +
                    listOfNotNull(PickerSource.NotInAlbum.takeIf { pool.notInAlbum }) +
                    pool.personIds.map { PickerSource.Person(it, "") }
                s.copy(sources = chips, source = chips.first())
            }
        },
    )
    val state: StateFlow<PickerState> = _state.asStateFlow()
    private var nextPage: Int? = 1
    private var generation = 0

    // Photo ids of hidden albums and (for person chips) chosen albums; looked up once, off the main thread.
    private var hiddenAlbumPhotos: Set<String>? = null
    private var poolAlbumPhotos: Set<String>? = null
    private var anyAlbumPhotos: Set<String>? = null

    private fun inAnyAlbum(): Set<String> = anyAlbumPhotos ?: library.albumPhotoIds(library.albums().map { it.id }).also { anyAlbumPhotos = it }

    fun loadSources() {
        scope.launch {
            runCatching {
                withContext(io) {
                    val albums = library.albums().sortedBy { it.name.lowercase() }
                    val people = library.people().filter { it.name.isNotBlank() }
                    if (narrowed) {
                        val names = albums.associate { it.id to it.name } + people.associate { it.id to it.name }
                        pool!!.albumIds.map { PickerSource.Album(it, names[it].orEmpty()) } +
                            listOfNotNull(PickerSource.NotInAlbum.takeIf { pool.notInAlbum }) +
                            pool.personIds.map { PickerSource.Person(it, names[it].orEmpty()) }
                    } else {
                        listOf(PickerSource.All, PickerSource.Favorites, PickerSource.NotInAlbum) +
                            albums.filter { it.id !in pool?.excludedAlbumIds.orEmpty() }.map { PickerSource.Album(it.id, it.name) } +
                            people.filter { it.id !in pool?.excludedPersonIds.orEmpty() }.map { PickerSource.Person(it.id, it.name) }
                    }
                }
            }.onSuccess { sources ->
                _state.update { s ->
                    // Keep the current chip, now with its name.
                    val current = sources.firstOrNull { it.sameAs(s.source) } ?: s.source
                    s.copy(sources = sources, source = current)
                }
            }
        }
    }

    private fun PickerSource.sameAs(other: PickerSource) = when {
        this is PickerSource.Album && other is PickerSource.Album -> id == other.id
        this is PickerSource.Person && other is PickerSource.Person -> id == other.id
        else -> this == other
    }

    /** What a page request asks the server for: the chip, plus "favorites only" from the pool. */
    private fun requestFor(source: PickerSource) = if (pool?.favoritesOnly == true) source.filter.copy(favoritesOnly = true) else source.filter

    /** Pool rules the server isn't asked to apply (see the class comment). Call on the io dispatcher. */
    private fun keeper(source: PickerSource): (com.wiggyserver.immichwallpaper.core.Asset) -> Boolean {
        val outsideAlbums = if (source == PickerSource.NotInAlbum) inAnyAlbum() else null
        val p = pool ?: return { a -> outsideAlbums == null || a.id !in outsideAlbums }
        if (hiddenAlbumPhotos == null) hiddenAlbumPhotos = if (p.excludedAlbumIds.isEmpty()) emptySet() else library.albumPhotoIds(p.excludedAlbumIds)
        // A person chip must also meet the album side: in a chosen album, or in none when "not in an album" is chosen.
        val needsAlbum = source is PickerSource.Person && (p.albumIds.isNotEmpty() || p.notInAlbum)
        if (needsAlbum && poolAlbumPhotos == null) poolAlbumPhotos = if (p.albumIds.isEmpty()) emptySet() else library.albumPhotoIds(p.albumIds)
        val noAlbum = if (needsAlbum && p.notInAlbum) inAnyAlbum() else null
        val needsPerson = (source is PickerSource.Album || source == PickerSource.NotInAlbum) && p.personIds.isNotEmpty()
        val hiddenIds = p.excludedAssetIds.toSet() + hiddenAlbumPhotos.orEmpty()
        return { a ->
            a.id !in hiddenIds &&
                (outsideAlbums == null || a.id !in outsideAlbums) &&
                a.personIds.none { it in p.excludedPersonIds } &&
                (!needsPerson || a.personIds.any { it in p.personIds }) &&
                (!needsAlbum || a.id in poolAlbumPhotos.orEmpty() || (noAlbum != null && a.id !in noAlbum))
        }
    }

    fun setSource(source: PickerSource) {
        generation++
        nextPage = 1
        _state.update { it.copy(source = source, photos = emptyList(), endReached = false, error = null, loading = false) }
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        val page = nextPage
        if (s.loading || page == null) return
        val gen = generation
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                // Pages whose photos are all filtered out are skipped, so scrolling never stalls on them.
                val (items, next) = withContext(io) {
                    val keep = keeper(s.source)
                    val found = mutableListOf<com.wiggyserver.immichwallpaper.core.Asset>()
                    var p: Int? = page
                    var pages = 0
                    while (p != null && found.isEmpty() && pages++ < MAX_PAGES_PER_LOAD) {
                        val result = library.photos(requestFor(s.source), p, PAGE_SIZE)
                        found += result.items.filter(keep)
                        p = result.nextPage
                    }
                    found to p
                }
                if (gen != generation) return@launch
                nextPage = next
                _state.update { it.copy(photos = it.photos + items, loading = false, endReached = next == null) }
            } catch (e: Exception) {
                if (gen == generation) _state.update { it.copy(loading = false, error = errorMessage(e, "load photos")) }
            }
        }
    }

    fun toggle(assetId: String) = _state.update { s ->
        when {
            assetId in s.selected -> s.copy(selected = s.selected - assetId)
            assetId in s.alreadyChosen || !s.canSelectMore -> s
            else -> s.copy(selected = s.selected + assetId)
        }
    }

    companion object {
        const val PAGE_SIZE = 60
        private const val MAX_PAGES_PER_LOAD = 10
    }
}
