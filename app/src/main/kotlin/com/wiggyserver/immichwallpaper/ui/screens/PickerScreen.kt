package com.wiggyserver.immichwallpaper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wiggyserver.immichwallpaper.presentation.PickerSource
import com.wiggyserver.immichwallpaper.presentation.PickerState
import com.wiggyserver.immichwallpaper.ui.AppIcons
import com.wiggyserver.immichwallpaper.ui.LocalPhotos
import com.wiggyserver.immichwallpaper.ui.NoticeCard
import com.wiggyserver.immichwallpaper.ui.ScreenScaffold
import com.wiggyserver.immichwallpaper.ui.Tone

class PickerActions(
    val setSource: (PickerSource) -> Unit,
    val toggle: (String) -> Unit,
    val loadMore: () -> Unit,
    val done: () -> Unit,
    val back: () -> Unit,
)

@Composable
fun PickerScreen(state: PickerState, actions: PickerActions, hiding: Boolean = false) {
    val title = when {
        hiding -> "Included photos"
        state.maxSelection == 1 -> "Choose a photo"
        else -> "Choose up to ${state.maxSelection}"
    }
    ScreenScaffold(
        title = title,
        onBack = actions.back,
        actions = {
            TextButton(onClick = actions.done, enabled = state.selected.isNotEmpty()) {
                Text(if (state.selected.isEmpty()) "Done" else "${if (hiding) "Hide" else "Add"} ${state.selected.size}")
            }
        },
    ) { padding ->
        val grid = rememberLazyGridState()
        val nearEnd by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= grid.layoutInfo.totalItemsCount - 12 } ?: true } }
        LaunchedEffect(nearEnd, state.photos.size, state.source) { if (nearEnd && !state.endReached && state.error == null) actions.loadMore() }

        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sources, key = { it.toString() }) { source ->
                    FilterChip(
                        selected = source == state.source,
                        onClick = { actions.setSource(source) },
                        label = { Text(source.label()) },
                        leadingIcon = when (source) {
                            PickerSource.Favorites -> ({ Icon(AppIcons.Star, null, Modifier.size(18.dp)) })
                            is PickerSource.Person -> ({ Icon(AppIcons.Person, null, Modifier.size(18.dp)) })
                            else -> null
                        },
                    )
                }
            }
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.photos, key = { it.id }) { asset ->
                    val order = state.selected.indexOf(asset.id)
                    val chosen = asset.id in state.alreadyChosen
                    PhotoCell(asset.id, order, chosen, enabled = order >= 0 || (!chosen && state.canSelectMore)) { actions.toggle(asset.id) }
                }
                if (state.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NoticeCard(AppIcons.Warning, "Couldn't load photos", state.error!!, Tone.ERROR, "Try again", actions.loadMore)
                    }
                } else if (state.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                } else if (state.endReached && state.photos.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("No photos here.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun PickerSource.label() = when (this) {
    PickerSource.All -> "All photos"
    PickerSource.Favorites -> "Favorites"
    PickerSource.NotInAlbum -> "Not in an album"
    is PickerSource.Album -> name.ifBlank { "Untitled album" }
    is PickerSource.Person -> name.ifBlank { "Person" }
}

@Composable
private fun PhotoCell(id: String, order: Int, alreadyChosen: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val selected = order >= 0
    val shape = RoundedCornerShape(if (selected) 18.dp else 6.dp)
    Box(
        Modifier.aspectRatio(1f).clip(shape)
            .let { if (selected) it.border(3.dp, MaterialTheme.colorScheme.primary, shape) else it }
            .clickable(enabled = enabled && !alreadyChosen, onClick = onClick),
    ) {
        LocalPhotos.current.Asset(id, Modifier.fillMaxSize().alpha(if (alreadyChosen || (!enabled && !selected)) 0.4f else 1f))
        when {
            selected -> Text(
                "${order + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape).padding(top = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            alreadyChosen -> Icon(
                AppIcons.Check, "Already an offline photo",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape).padding(3.dp),
            )
        }
    }
}
