package com.wiggyserver.immichwallpaper.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wiggyserver.immichwallpaper.data.SlotStatus
import com.wiggyserver.immichwallpaper.presentation.OfflineSetState
import com.wiggyserver.immichwallpaper.presentation.SlotView
import com.wiggyserver.immichwallpaper.ui.AppIcons
import com.wiggyserver.immichwallpaper.ui.LocalPhotos
import com.wiggyserver.immichwallpaper.ui.MaxContentWidth
import com.wiggyserver.immichwallpaper.ui.NoticeCard
import com.wiggyserver.immichwallpaper.ui.ScreenScaffold
import com.wiggyserver.immichwallpaper.ui.Tone

class OfflineSetActions(
    val pick: () -> Unit,
    /** Opens the picker to replace these slots. */
    val replace: (List<Int>) -> Unit,
    val remove: (List<Int>) -> Unit,
    val replaceRandomly: (List<Int>) -> Unit,
    val fillRandomly: () -> Unit,
    val toggleSelected: (Int) -> Unit,
    val clearSelection: () -> Unit,
)

@Composable
fun OfflineSetScreen(state: OfflineSetState, wide: Boolean, actions: OfflineSetActions, onBack: (() -> Unit)? = null) {
    ScreenScaffold("Offline photos", onBack = onBack) { padding ->
        OfflineSetContent(state, wide, actions, padding)
    }
}

/** Also used as an onboarding step, without the top bar. */
@Composable
fun OfflineSetContent(state: OfflineSetState, wide: Boolean, actions: OfflineSetActions, padding: PaddingValues) {
    BackHandler(enabled = state.selected.isNotEmpty()) { actions.clearSelection() }
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (wide) 5 else 3),
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Shown in order when your Immich server can't be reached. They're kept on the phone, so they work in airplane mode too.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.selected.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = actions.pick, enabled = state.free > 0 && !state.busy) {
                                Icon(AppIcons.Add, null, Modifier.size(18.dp))
                                Text("Add photos", Modifier.padding(start = 8.dp))
                            }
                            OutlinedButton(onClick = actions.fillRandomly, enabled = state.free > 0 && !state.busy) {
                                Icon(AppIcons.Shuffle, null, Modifier.size(18.dp))
                                Text("Fill randomly", Modifier.padding(start = 8.dp))
                            }
                        }
                    } else {
                        SelectionBar(state, actions)
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.message?.let { NoticeCard(AppIcons.Warning, "Heads up", it, Tone.WARNING) }
                    Text("${state.ready} of 10 ready", style = MaterialTheme.typography.titleSmall)
                    if (state.selected.isEmpty() && state.free < 10) {
                        Text(
                            "Tap the shuffle button on a photo for a different random one. Touch and hold to select several.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.slots, key = { it.index }) { slot -> SlotTile(slot, slot.index in state.selected, state.selected.isNotEmpty(), state.busy, actions) }
        }
    }
}

/** Group actions for the selected photos. */
@Composable
private fun SelectionBar(state: OfflineSetState, actions: OfflineSetActions) {
    val chosen = state.selected.sorted()
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.clearSelection) { Icon(AppIcons.Close, "Clear selection") }
                Text("${chosen.size} selected", style = MaterialTheme.typography.titleSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                Button(onClick = { actions.replace(chosen) }, enabled = !state.busy, modifier = Modifier.weight(1f), contentPadding = TightPadding) { Text("Replace") }
                OutlinedButton(onClick = { actions.replaceRandomly(chosen) }, enabled = !state.busy, modifier = Modifier.weight(1f), contentPadding = TightPadding) {
                    Icon(AppIcons.Shuffle, null, Modifier.size(16.dp))
                    Text("Random", Modifier.padding(start = 4.dp))
                }
                OutlinedButton(onClick = { actions.remove(chosen) }, enabled = !state.busy, modifier = Modifier.weight(1f), contentPadding = TightPadding) { Text("Remove") }
            }
        }
    }
}

private val TightPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

@Composable
private fun SlotTile(slot: SlotView, selected: Boolean, selecting: Boolean, busy: Boolean, actions: OfflineSetActions) {
    val shape = RoundedCornerShape(14.dp)
    var menu by remember { mutableStateOf(false) }
    val id = slot.assetId
    Box(
        Modifier.aspectRatio(0.75f).clip(shape)
            .let {
                when {
                    selected -> it.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape)
                    id == null -> it.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                    else -> it
                }
            }
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = {
                    when {
                        selecting -> actions.toggleSelected(slot.index)
                        id == null -> actions.pick()
                        else -> menu = true
                    }
                },
                onLongClick = { if (id != null) actions.toggleSelected(slot.index) },
            ),
    ) {
        if (id == null) {
            Icon(AppIcons.Add, "Add a photo", Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LocalPhotos.current.Asset(id, Modifier.fillMaxSize())
            val badge = when (slot.status) {
                SlotStatus.PENDING -> AppIcons.CloudOff to "Waiting to download"
                SlotStatus.MISSING -> AppIcons.Warning to "Deleted on the server"
                else -> null
            }
            if (badge != null) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    Icon(badge.first, badge.second, Modifier.padding(4.dp).size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (selecting) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    border = if (selected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp),
                ) {
                    if (selected) Icon(AppIcons.Check, "Selected", Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                // One tap for a different random photo, again and again until it's one you like.
                Surface(
                    onClick = { actions.replaceRandomly(listOf(slot.index)) },
                    enabled = !busy,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(36.dp),
                ) {
                    Icon(AppIcons.Shuffle, "Different random photo", Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
            Text(
                "${slot.index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Replace") }, onClick = { menu = false; actions.replace(listOf(slot.index)) })
                DropdownMenuItem(text = { Text("Replace with a random photo") }, onClick = { menu = false; actions.replaceRandomly(listOf(slot.index)) })
                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; actions.remove(listOf(slot.index)) })
                DropdownMenuItem(text = { Text("Select") }, onClick = { menu = false; actions.toggleSelected(slot.index) })
            }
        }
    }
}
