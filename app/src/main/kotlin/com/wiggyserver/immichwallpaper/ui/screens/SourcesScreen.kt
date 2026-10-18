package com.wiggyserver.immichwallpaper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wiggyserver.immichwallpaper.presentation.SourcesMode
import com.wiggyserver.immichwallpaper.presentation.SourcesState
import com.wiggyserver.immichwallpaper.presentation.describeSources
import com.wiggyserver.immichwallpaper.presentation.favoritesWarning
import com.wiggyserver.immichwallpaper.data.Album
import com.wiggyserver.immichwallpaper.ui.AppIcons
import com.wiggyserver.immichwallpaper.ui.Group
import com.wiggyserver.immichwallpaper.ui.LocalPhotos
import com.wiggyserver.immichwallpaper.ui.MaxContentWidth
import com.wiggyserver.immichwallpaper.ui.NoticeCard
import com.wiggyserver.immichwallpaper.ui.ScreenScaffold
import com.wiggyserver.immichwallpaper.ui.SectionTitle
import com.wiggyserver.immichwallpaper.ui.SwitchRow
import com.wiggyserver.immichwallpaper.ui.Tone

class SourcesActions(
    val setMode: (SourcesMode) -> Unit,
    val toggleAlbum: (String) -> Unit,
    val togglePerson: (String) -> Unit,
    val setFavoritesOnly: (Boolean) -> Unit,
    val setNotInAlbum: (Boolean) -> Unit,
    val clear: () -> Unit,
    val save: () -> Unit,
    val retry: () -> Unit,
    val unhidePhoto: (String) -> Unit,
    val pickPhotosToHide: () -> Unit,
)

@Composable
fun SourcesScreen(state: SourcesState, hiddenPhotos: List<String>, actions: SourcesActions) {
    ScreenScaffold(
        "Photo sources",
        bottomBar = {
            if (state.dirty) {
                Surface(tonalElevation = 3.dp) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = actions.save, modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) { Text("Save changes") }
                    }
                }
            }
        },
    ) { padding -> SourcesContent(state, hiddenPhotos, actions, padding) }
}

/** Also used as an onboarding step (which saves on "Next"). */
@Composable
fun SourcesContent(state: SourcesState, hiddenPhotos: List<String>, actions: SourcesActions, padding: PaddingValues) {
    val hiding = state.mode == SourcesMode.HIDE
    // Once "Show from" picks albums or people, hiding works on photos in that pool instead of whole albums.
    val narrowed = state.draft.albumIds.isNotEmpty() || state.draft.personIds.isNotEmpty() || state.draft.notInAlbum
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wallpapers are picked at random from:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(describeSources(state.draft, state.albums, state.people), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (state.draft.albumIds.isNotEmpty() || state.draft.personIds.isNotEmpty() || state.draft.favoritesOnly || state.draft.notInAlbum) {
                            TextButton(onClick = actions.clear) { Text("Use all photos") }
                        }
                    }
                    state.notice?.let { NoticeCard(AppIcons.Warning, "Sources changed", it, Tone.WARNING) }
                    ModeSwitch(state.mode, actions.setMode)
                    Text(
                        if (hiding && narrowed) {
                            "Browse the photos your sources include and hide any you don't want. Photos showing a hidden person are left out too."
                        } else if (hiding) {
                            "Photos in these albums, or showing these people, never become wallpapers."
                        } else {
                            "Photos from any chosen album. With people chosen too, only those photos showing one of them."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!hiding) FavoritesOnly(state, actions.setFavoritesOnly)
                }
            }
            when {
                state.error != null -> item { NoticeCard(AppIcons.Warning, "Couldn't load your library", state.error!!, Tone.ERROR, "Try again", actions.retry) }
                state.loading -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else -> {
                    if (hiding) hiddenPhotosSection(hiddenPhotos, actions)
                    val showAlbums = !(hiding && narrowed)
                    if (showAlbums) section("Albums", state.albums.isEmpty(), "No albums on this server.")
                    if (!hiding) {
                        item(key = "not-in-album") {
                            PickRow("Not in an album", "Photos you haven't put in any album", state.draft.notInAlbum, { actions.setNotInAlbum(!state.draft.notInAlbum) }, hiding = false) { mod ->
                                Box(mod.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                    Icon(AppIcons.Offline, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                    items(if (showAlbums) state.albums else emptyList(), key = { "a" + it.id }) { album ->
                        PickRow(
                            title = album.name.ifBlank { "Untitled album" },
                            subtitle = albumSubtitle(album, state, hiding),
                            checked = album.id in (if (hiding) state.draft.excludedAlbumIds else state.draft.albumIds),
                            hiding = hiding,
                            onClick = { actions.toggleAlbum(album.id) },
                        ) { mod ->
                            val cover = album.thumbnailAssetId
                            if (cover != null) LocalPhotos.current.Asset(cover, mod.clip(RoundedCornerShape(10.dp))) else Box(mod)
                        }
                    }
                    section("People", state.people.isEmpty(), "No named people. Name faces in Immich to choose them here.")
                    items(state.people, key = { "p" + it.id }) { person ->
                        PickRow(person.name, null, person.id in (if (hiding) state.draft.excludedPersonIds else state.draft.personIds), { actions.togglePerson(person.id) }, hiding) { mod ->
                            LocalPhotos.current.Person(person.id, mod.clip(CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(mode: SourcesMode, onChange: (SourcesMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SourcesMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onChange(m) },
                shape = SegmentedButtonDefaults.itemShape(i, SourcesMode.entries.size),
                label = { Text(if (m == SourcesMode.SHOW) "Show from" else "Never show") },
            )
        }
    }
}

/** Single hidden photos (from Home's "Never show" or the picker), each with an un-hide button. */
private fun LazyListScope.hiddenPhotosSection(ids: List<String>, actions: SourcesActions) {
    item(key = "hidden-title") { SectionTitle("Hidden photos") }
    item(key = "hidden-row") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ids.isEmpty()) {
                Text("None yet. Use \"Never show\" on Home, or browse your included photos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ids, key = { it }) { id ->
                        Box(Modifier.size(84.dp).clip(RoundedCornerShape(12.dp))) {
                            LocalPhotos.current.Asset(id, Modifier.fillMaxSize())
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clickable { actions.unhidePhoto(id) },
                            ) {
                                Icon(AppIcons.Close, "Show this photo again", Modifier.padding(6.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                            }
                        }
                    }
                }
                Text("${ids.size} hidden. Changes here apply right away.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = actions.pickPhotosToHide) { Text("Included photos") }
        }
    }
}

private fun LazyListScope.section(title: String, empty: Boolean, emptyText: String) {
    item(key = title) { SectionTitle(title) }
    if (empty) item(key = "$title-empty") { Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun PickRow(title: String, subtitle: String?, checked: Boolean, onClick: () -> Unit, hiding: Boolean, image: @Composable (Modifier) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        image(Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val colors = if (hiding) CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error, checkmarkColor = MaterialTheme.colorScheme.onError) else CheckboxDefaults.colors()
        Checkbox(checked, onCheckedChange = null, colors = colors)
    }
}

/** "Favorites only", explained before it's turned on, with a warning when it would leave nothing to show. */
@Composable
private fun FavoritesOnly(state: SourcesState, setFavoritesOnly: (Boolean) -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val on = state.draft.favoritesOnly
    Group {
        SwitchRow(
            "Favorites only",
            when {
                !on -> "Only photos marked as favorites in Immich"
                state.favoriteTotal != null -> "Only favorites from the sources above. ${state.favoriteTotal} in your library."
                else -> "Only favorites from the sources above"
            },
            on,
        ) { if (it) confirming = true else setFavoritesOnly(false) }
    }
    favoritesWarning(state)?.let { NoticeCard(AppIcons.Warning, "Nothing to show", it, Tone.WARNING) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Show favorites only?") },
            text = {
                Text(
                    "This will only show photos that have been marked as favorites in Immich, from the sources you have selected. " +
                        "Album counts change to show how many favorites each one has.",
                )
            },
            confirmButton = { TextButton(onClick = { confirming = false; setFavoritesOnly(true) }) { Text("Turn on") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

/** "162 items", or with favorites only "12 favorites of 162 items". */
private fun albumSubtitle(album: Album, state: SourcesState, hiding: Boolean): String {
    if (hiding || !state.draft.favoritesOnly) return "${album.assetCount} items"
    val favorites = state.favoriteCounts[album.id] ?: return "${album.assetCount} items · counting favorites…"
    return "${if (favorites == 1) "1 favorite" else "$favorites favorites"} of ${album.assetCount} items"
}
