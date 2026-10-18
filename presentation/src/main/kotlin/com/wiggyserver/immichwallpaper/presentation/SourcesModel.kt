package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.data.Album
import com.wiggyserver.immichwallpaper.data.Person
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

/** Whether the album and people lists choose what to show or what never to show. */
enum class SourcesMode { SHOW, HIDE }

data class SourcesState(
    val albums: List<Album> = emptyList(),
    val people: List<Person> = emptyList(),
    val draft: SourceFilter = SourceFilter(),
    val saved: SourceFilter = SourceFilter(),
    val loading: Boolean = false,
    val error: String? = null,
    val mode: SourcesMode = SourcesMode.SHOW,
    /** Favorite photos per album id, filled in while "Favorites only" is on. */
    val favoriteCounts: Map<String, Int> = emptyMap(),
    /** Favorite photos in the whole library (null until counted). */
    val favoriteTotal: Int? = null,
    /** Chosen albums or people that the server no longer has (taken out of the draft). */
    val notice: String? = null,
) {
    // Hidden photos are applied immediately, outside the draft.
    val dirty: Boolean get() = draft.copy(excludedAssetIds = emptyList()) != saved.copy(excludedAssetIds = emptyList())
}

/** Which photos the wallpaper draws from. Edits are a draft: saving rebuilds the queue once. */
class SourcesModel(
    private val app: AppPort,
    private val library: PhotoLibrary,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(app.config.value.filter.let { SourcesState(draft = it, saved = it) })
    val state: StateFlow<SourcesState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                val (albums, allPeople) = withContext(io) { library.albums() to library.people() }
                val people = allPeople.filter { it.name.isNotBlank() }
                _state.update { it.copy(albums = albums.sortedBy { a -> a.name.lowercase() }, people = people, loading = false) }
                dropMissing(albums.map { it.id }.toSet(), allPeople.map { it.id }.toSet())
                if (_state.value.draft.favoritesOnly) countFavorites()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = errorMessage(e, "load albums and people")) }
            }
        }
    }

    /** Deleted or unshared albums and removed people would otherwise stay chosen, invisibly. */
    private fun dropMissing(albums: Set<String>, people: Set<String>) {
        val f = _state.value.draft
        val goneAlbums = (f.albumIds + f.excludedAlbumIds).filter { it !in albums }.distinct()
        val gonePeople = (f.personIds + f.excludedPersonIds).filter { it !in people }.distinct()
        if (goneAlbums.isEmpty() && gonePeople.isEmpty()) return
        val what = listOfNotNull(
            goneAlbums.size.takeIf { it > 0 }?.let { plural(it, "album") },
            gonePeople.size.takeIf { it > 0 }?.let { plural(it, "person", "people") },
        ).joinToString(" and ")
        _state.update {
            it.copy(
                draft = f.copy(
                    albumIds = f.albumIds - goneAlbums.toSet(), excludedAlbumIds = f.excludedAlbumIds - goneAlbums.toSet(),
                    personIds = f.personIds - gonePeople.toSet(), excludedPersonIds = f.excludedPersonIds - gonePeople.toSet(),
                ),
                notice = "$what you chose " +
                    (if (goneAlbums.size + gonePeople.size == 1) "no longer exists or isn't shared with you." else "no longer exist or aren't shared with you.") +
                    " Save to update your sources.",
            )
        }
    }

    fun setMode(mode: SourcesMode) = _state.update { it.copy(mode = mode) }

    /** In SHOW mode includes the album, in HIDE mode hides it; never both. */
    fun toggleAlbum(id: String) = edit {
        if (_state.value.mode == SourcesMode.SHOW) it.copy(albumIds = it.albumIds.toggle(id), excludedAlbumIds = it.excludedAlbumIds - id)
        else it.copy(excludedAlbumIds = it.excludedAlbumIds.toggle(id), albumIds = it.albumIds - id)
    }

    fun togglePerson(id: String) = edit {
        if (_state.value.mode == SourcesMode.SHOW) it.copy(personIds = it.personIds.toggle(id), excludedPersonIds = it.excludedPersonIds - id)
        else it.copy(excludedPersonIds = it.excludedPersonIds.toggle(id), personIds = it.personIds - id)
    }

    fun hidePhotos(ids: List<String>) = app.hidePhotos(ids)
    fun unhidePhoto(id: String) = app.unhidePhoto(id)
    fun setFavoritesOnly(on: Boolean) {
        edit { it.copy(favoritesOnly = on) }
        if (on) countFavorites()
    }

    private var counting = false

    /** Library total first, then the chosen albums, then the rest; each shows up as soon as it's known. */
    private fun countFavorites() {
        if (counting || _state.value.favoriteTotal != null) return
        counting = true
        scope.launch {
            try {
                val total = withContext(io) { library.favoriteCount(null) }
                _state.update { it.copy(favoriteTotal = total) }
                val chosen = _state.value.draft.albumIds
                val order = _state.value.albums.map { it.id }.sortedBy { it !in chosen }
                for (id in order) {
                    val n = withContext(io) { library.favoriteCount(id) }
                    _state.update { it.copy(favoriteCounts = it.favoriteCounts + (id to n)) }
                }
            } catch (e: Exception) {
                // Counts are a hint: without them the albums show their usual totals.
            } finally {
                counting = false
            }
        }
    }

    /** "Not in an album" works like one more album on the Show from side. */
    fun setNotInAlbum(on: Boolean) = edit { it.copy(notInAlbum = on) }
    /** Back to all photos (hidden albums, people and photos stay hidden). */
    fun clear() = edit { it.copy(albumIds = emptyList(), personIds = emptyList(), favoritesOnly = false, notInAlbum = false) }

    fun save() {
        val draft = _state.value.draft
        app.updateConfig { it.copy(filter = draft.copy(excludedAssetIds = it.filter.excludedAssetIds)) }
        _state.update { it.copy(saved = draft, notice = null) }
    }

    private fun edit(change: (SourceFilter) -> SourceFilter) = _state.update { it.copy(draft = change(it.draft)) }

    private fun List<String>.toggle(id: String) = if (id in this) this - id else this + id
}

/** "All photos", "Trip · Ana · favorites only", "3 albums" … */
fun describeSources(filter: SourceFilter, albums: List<Album>, people: List<Person>): String {
    // Up to two names, then a count: "Trip · Family", "3 albums".
    fun names(ids: List<String>, known: Map<String, String>, one: String, many: String): String {
        val named = ids.mapNotNull { known[it]?.ifBlank { null } }
        return if (ids.size <= 2 && named.size == ids.size) named.joinToString(" · ") else plural(ids.size, one, many)
    }
    val parts = buildList {
        if (filter.albumIds.isNotEmpty()) add(names(filter.albumIds, albums.associate { it.id to it.name }, "album", "albums"))
        if (filter.notInAlbum) add("Not in an album")
        if (filter.personIds.isNotEmpty()) add(names(filter.personIds, people.associate { it.id to it.name }, "person", "people"))
    }
    val base = when {
        parts.isEmpty() && filter.favoritesOnly -> "Favorites"
        parts.isEmpty() -> "All photos"
        filter.favoritesOnly -> (parts + "favorites only").joinToString(" · ")
        else -> parts.joinToString(" · ")
    }
    val hidden = buildList {
        if (filter.excludedAlbumIds.isNotEmpty()) add(names(filter.excludedAlbumIds, albums.associate { it.id to it.name }, "album", "albums"))
        if (filter.excludedPersonIds.isNotEmpty()) add(names(filter.excludedPersonIds, people.associate { it.id to it.name }, "person", "people"))
    }
    return if (hidden.isEmpty()) base else "$base, except ${hidden.joinToString(" · ")}"
}

/** Shown with "Favorites only" when it would leave nothing to show; null otherwise (or not yet known). */
fun favoritesWarning(s: SourcesState): String? {
    val f = s.draft
    if (!f.favoritesOnly) return null
    if (s.favoriteTotal == 0) return "There are no favorites in your library, so no photos will be shown."
    if (f.albumIds.isEmpty() || f.notInAlbum || f.personIds.isNotEmpty()) return null
    val counts = f.albumIds.map { s.favoriteCounts[it] ?: return null }
    if (counts.sum() > 0) return null
    val name = f.albumIds.singleOrNull()?.let { id -> s.albums.firstOrNull { it.id == id }?.name }
    return if (name != null) "$name has no favorites, so no photos will be shown."
    else "Your chosen albums have no favorites, so no photos will be shown."
}
