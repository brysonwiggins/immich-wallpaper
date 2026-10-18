package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.data.AppConfig
import com.wiggyserver.immichwallpaper.data.ConnectionResult
import com.wiggyserver.immichwallpaper.data.FailReason
import com.wiggyserver.immichwallpaper.data.ImmichException
import com.wiggyserver.immichwallpaper.data.SlotResult
import com.wiggyserver.immichwallpaper.data.SlotStatus
import com.wiggyserver.immichwallpaper.data.SourceFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
private fun test(block: suspend TestScope.(TestDispatcher) -> Unit) = runTest(UnconfinedTestDispatcher()) {
    block(UnconfinedTestDispatcher(testScheduler))
}

class ModelsTest {
    @Nested
    inner class Server {
        @Test
        fun `starts from the saved url and an empty key field`() = test { io ->
            val app = FakeApp(AppConfig(serverUrl = "https://photos.example")).apply { savedKey = "k" }
            val m = ServerModel(app, this, io)
            assertEquals("https://photos.example", m.state.value.url)
            assertEquals("", m.state.value.key)
            assertTrue(m.state.value.hasSavedKey)
        }

        @Test
        fun `a successful test saves the server and key`() = test { io ->
            val app = FakeApp()
            val m = ServerModel(app, this, io)
            m.setUrl(" https://photos.example/ ")
            m.setKey(" secret ")
            m.test()
            assertEquals(listOf("https://photos.example/" to "secret"), app.tested)
            assertEquals(listOf("https://photos.example/" to "secret"), app.saved)
            assertEquals(ServerStatus.Connected("3.2.0"), m.state.value.status)
            assertEquals("", m.state.value.key)
        }

        @Test
        fun `a failed test saves nothing and explains why`() = test { io ->
            val app = FakeApp().apply { connection = ConnectionResult.Unauthorized }
            val m = ServerModel(app, this, io)
            m.setUrl("https://photos.example")
            m.setKey("bad")
            m.test()
            assertTrue(app.saved.isEmpty())
            assertEquals(ServerStatus.Failed("The server rejected this API key."), m.state.value.status)
        }

        @Test
        fun `an existing key is reused when the key field is left empty`() = test { io ->
            val app = FakeApp(AppConfig(serverUrl = "https://old")).apply { savedKey = "k" }
            val m = ServerModel(app, this, io)
            m.setUrl("https://new")
            m.test()
            assertEquals(listOf("https://new" to null), app.tested)
            assertEquals(listOf("https://new" to null), app.saved)
        }

        @Test
        fun `input is checked before contacting the server`() = test { io ->
            val app = FakeApp()
            val m = ServerModel(app, this, io)
            m.setUrl("photos.example")
            m.setKey("k")
            m.test()
            assertEquals(ServerStatus.Failed("Start the address with https:// or http://"), m.state.value.status)
            m.setUrl("https://photos.example")
            m.setKey(" ")
            m.test()
            assertEquals(ServerStatus.Failed("Enter your Immich API key."), m.state.value.status)
            assertTrue(app.tested.isEmpty())
        }

        @Test
        fun `editing clears the last result`() = test { io ->
            val app = FakeApp().apply { connection = ConnectionResult.Error(500) }
            val m = ServerModel(app, this, io)
            m.setUrl("https://p")
            m.setKey("k")
            m.test()
            assertEquals(ServerStatus.Failed("The server answered with an error (HTTP 500)."), m.state.value.status)
            m.setKey("k2")
            assertEquals(ServerStatus.Idle, m.state.value.status)
        }

        @Test
        fun `a home-network server asks for the local network permission first`() = test { io ->
            val app = FakeApp().apply { localAllowed = false; localServer = true }
            val m = ServerModel(app, this, io)
            m.setUrl("http://192.168.0.7:2283")
            m.setKey("k")
            m.test()
            assertEquals(ServerStatus.NeedsLocalNetwork, m.state.value.status)
            assertTrue(app.tested.isEmpty())
            app.localAllowed = true
            m.localNetworkResult(granted = true)
            assertEquals(ServerStatus.Connected("3.2.0"), m.state.value.status)
        }

        @Test
        fun `denying the permission explains how to allow it later`() = test { io ->
            val app = FakeApp().apply { localAllowed = false; localServer = true }
            val m = ServerModel(app, this, io)
            m.setUrl("http://nas.lan")
            m.setKey("k")
            m.test()
            m.localNetworkResult(granted = false)
            val status = m.state.value.status as ServerStatus.Failed
            assertTrue(status.message.contains("Nearby devices"), status.message)
            assertTrue(app.saved.isEmpty())
        }

        @Test
        fun `an unreachable server without the permission may be local, so the permission is offered`() = test { io ->
            val app = FakeApp().apply { localAllowed = false; connection = ConnectionResult.Unreachable("EPERM") }
            val m = ServerModel(app, this, io)
            m.setUrl("http://my-nas:2283")
            m.setKey("k")
            m.test()
            assertEquals(ServerStatus.NeedsLocalNetwork, m.state.value.status)
            app.localAllowed = true // granted elsewhere; a reachable failure is reported normally
            m.test()
            assertEquals(ServerStatus.Failed(connectionMessage(ConnectionResult.Unreachable("x"))!!), m.state.value.status)
        }

        @Test
        fun `connection messages`() {
            assertEquals("Can't reach the server. Check the address, and that this phone can reach it (VPN, Wi-Fi).",
                connectionMessage(ConnectionResult.Unreachable("timeout")))
            assertNull(connectionMessage(ConnectionResult.Ok("1.0.0")))
            assertEquals("This API key is missing permissions: asset.view, face.read. Edit the key in Immich's web app and tick them.",
                connectionMessage(ConnectionResult.MissingPermissions(listOf("asset.view", "face.read"))))
            assertEquals("The API key is missing the album.read permission. Edit it in Immich's web app.",
                errorMessage(ImmichException.Unauthorized("album.read"), "load albums"))
        }
    }

    @Nested
    inner class Sources {
        @Test
        fun `loads albums and people and starts from the saved selection`() = test { io ->
            val app = FakeApp(AppConfig(filter = SourceFilter(albumIds = listOf("a2"))))
            val m = SourcesModel(app, FakeLibrary(), this, io)
            m.load()
            val s = m.state.value
            assertEquals(listOf("a2", "a1"), s.albums.map { it.id }) // alphabetical: Family, Trip
            assertEquals(listOf("p1"), s.people.map { it.id }) // unnamed people are hidden
            assertEquals(SourceFilter(albumIds = listOf("a2")), s.draft)
            assertFalse(s.loading)
            assertFalse(s.dirty)
        }

        @Test
        fun `favorites only counts each album's favorites, chosen albums first`() = test { io ->
            val lib = FakeLibrary().apply { favorites = { id -> mapOf("a1" to 3, "a2" to 0)[id] ?: 7 } }
            val app = FakeApp(AppConfig(filter = SourceFilter(albumIds = listOf("a1"))))
            val m = SourcesModel(app, lib, this, io)
            m.load()
            assertTrue(lib.favoriteLookups.isEmpty()) // nothing counted while favorites only is off
            m.setFavoritesOnly(true)
            assertEquals(listOf(null, "a1", "a2"), lib.favoriteLookups)
            assertEquals(mapOf("a1" to 3, "a2" to 0), m.state.value.favoriteCounts)
            assertEquals(7, m.state.value.favoriteTotal)
            m.setFavoritesOnly(false)
            m.setFavoritesOnly(true)
            assertEquals(3, lib.favoriteLookups.size) // counted once
        }

        @Test
        fun `favorites only is counted at load when it is already on`() = test { io ->
            val lib = FakeLibrary().apply { favorites = { 1 } }
            val m = SourcesModel(FakeApp(AppConfig(filter = SourceFilter(favoritesOnly = true))), lib, this, io)
            m.load()
            assertEquals(mapOf("a1" to 1, "a2" to 1), m.state.value.favoriteCounts)
        }

        @Test
        fun `favorites only warns when the chosen sources have no favorites`() = test { io ->
            val lib = FakeLibrary().apply { favorites = { id -> if (id == "a2") 0 else 4 } }
            val m = SourcesModel(FakeApp(), lib, this, io)
            m.load()
            m.toggleAlbum("a2")
            m.setFavoritesOnly(true)
            assertEquals("Family has no favorites, so no photos will be shown.", favoritesWarning(m.state.value))
            m.toggleAlbum("a1")
            assertNull(favoritesWarning(m.state.value))
            m.toggleAlbum("a1"); m.toggleAlbum("a2")
            assertNull(favoritesWarning(m.state.value)) // all photos: the library has favorites
            m.setFavoritesOnly(false)
            assertNull(favoritesWarning(m.state.value))

            val empty = SourcesModel(FakeApp(), FakeLibrary(), this, io) // no favorites anywhere
            empty.load()
            empty.setFavoritesOnly(true)
            assertEquals("There are no favorites in your library, so no photos will be shown.", favoritesWarning(empty.state.value))
        }

        @Test
        fun `a failed favorite count leaves the counts out instead of failing the screen`() = test { io ->
            val lib = FakeLibrary()
            val m = SourcesModel(FakeApp(), lib, this, io)
            m.load()
            lib.failure = unreachable
            m.setFavoritesOnly(true)
            assertEquals(emptyMap<String, Int>(), m.state.value.favoriteCounts)
            assertNull(m.state.value.error)
            assertNull(favoritesWarning(m.state.value))
        }

        @Test
        fun `chosen albums and people that are gone are taken out, with a note to save`() = test { io ->
            val saved = SourceFilter(albumIds = listOf("a1", "gone"), personIds = listOf("p-gone"), excludedAlbumIds = listOf("gone-2"))
            val app = FakeApp(AppConfig(filter = saved))
            val m = SourcesModel(app, FakeLibrary(), this, io)
            m.load()
            val s = m.state.value
            assertEquals(SourceFilter(albumIds = listOf("a1")), s.draft)
            assertEquals("2 albums and 1 person you chose no longer exist or aren't shared with you. Save to update your sources.", s.notice)
            assertTrue(s.dirty)
            m.save()
            assertEquals(SourceFilter(albumIds = listOf("a1")), app.config.value.filter)
            assertNull(m.state.value.notice)

            // One gone; an unnamed person (p2: hidden from the list) is not gone.
            val one = SourcesModel(FakeApp(AppConfig(filter = SourceFilter(albumIds = listOf("gone"), personIds = listOf("p2")))), FakeLibrary(), this, io)
            one.load()
            assertEquals("1 album you chose no longer exists or isn't shared with you. Save to update your sources.", one.state.value.notice)
            assertEquals(listOf("p2"), one.state.value.draft.personIds)
        }

        @Test
        fun `edits stay a draft until saved`() = test { io ->
            val app = FakeApp()
            val m = SourcesModel(app, FakeLibrary(), this, io)
            m.load()
            m.toggleAlbum("a1")
            m.togglePerson("p1")
            m.setFavoritesOnly(true)
            assertTrue(m.state.value.dirty)
            assertEquals(SourceFilter(), app.config.value.filter)
            m.save()
            assertEquals(SourceFilter(listOf("a1"), listOf("p1"), true), app.config.value.filter)
            assertFalse(m.state.value.dirty)
            m.toggleAlbum("a1")
            m.togglePerson("p1")
            assertEquals(SourceFilter(favoritesOnly = true), m.state.value.draft)
            m.setNotInAlbum(true)
            assertTrue(m.state.value.draft.notInAlbum)
            m.clear()
            assertEquals(SourceFilter(), m.state.value.draft)
        }

        @Test
        fun `never-show mode hides albums and people, and an item is never both shown and hidden`() = test { io ->
            val app = FakeApp()
            val m = SourcesModel(app, FakeLibrary(), this, io)
            m.toggleAlbum("a1")
            m.setMode(SourcesMode.HIDE)
            assertEquals(SourcesMode.HIDE, m.state.value.mode)
            m.toggleAlbum("a1") // was shown: now hidden instead
            m.toggleAlbum("a2")
            m.togglePerson("p1")
            assertEquals(SourceFilter(excludedAlbumIds = listOf("a1", "a2"), excludedPersonIds = listOf("p1")), m.state.value.draft)
            m.setMode(SourcesMode.SHOW)
            m.togglePerson("p1") // was hidden: now shown instead
            assertEquals(listOf("p1"), m.state.value.draft.personIds)
            assertEquals(emptyList<String>(), m.state.value.draft.excludedPersonIds)
            m.save()
            assertEquals(listOf("a1", "a2"), app.config.value.filter.excludedAlbumIds)
        }

        @Test
        fun `hidden photos apply at once and survive saving other edits`() = test { io ->
            val app = FakeApp()
            val m = SourcesModel(app, FakeLibrary(), this, io)
            m.toggleAlbum("a1")
            m.hidePhotos(listOf("x1", "x2"))
            m.unhidePhoto("x1")
            assertEquals(listOf("x2"), app.config.value.filter.excludedAssetIds)
            m.save()
            assertEquals(listOf("x2"), app.config.value.filter.excludedAssetIds)
            assertEquals(listOf("a1"), app.config.value.filter.albumIds)
            assertFalse(m.state.value.dirty)
        }

        @Test
        fun `a load failure is shown and can be retried`() = test { io ->
            val lib = FakeLibrary().apply { failure = unreachable }
            val m = SourcesModel(FakeApp(), lib, this, io)
            m.load()
            assertEquals("Can't reach your Immich server.", m.state.value.error)
            lib.failure = null
            m.load()
            assertNull(m.state.value.error)
            assertEquals(2, m.state.value.albums.size)
        }

        @Test
        fun `describes the selection in words`() {
            val lib = FakeLibrary()
            assertEquals("All photos", describeSources(SourceFilter(), lib.albums, lib.people))
            assertEquals("Favorites", describeSources(SourceFilter(favoritesOnly = true), lib.albums, lib.people))
            assertEquals("Trip · Ana · favorites only", describeSources(SourceFilter(listOf("a1"), listOf("p1"), true), lib.albums, lib.people))
            assertEquals("Trip · Family", describeSources(SourceFilter(listOf("a1", "a2")), lib.albums, lib.people))
            assertEquals("3 albums", describeSources(SourceFilter(listOf("a1", "a2", "a9")), lib.albums, lib.people))
            assertEquals("2 albums", describeSources(SourceFilter(listOf("a1", "gone")), lib.albums, lib.people))
            assertEquals("All photos, except Trip · Ana", describeSources(SourceFilter(excludedAlbumIds = listOf("a1"), excludedPersonIds = listOf("p1")), lib.albums, lib.people))
            assertEquals("Trip · Not in an album", describeSources(SourceFilter(listOf("a1"), notInAlbum = true), lib.albums, lib.people))
            assertEquals("Not in an album", describeSources(SourceFilter(notInAlbum = true), lib.albums, lib.people))
            assertEquals("Family, except 3 people", describeSources(SourceFilter(listOf("a2"), excludedPersonIds = listOf("p1", "p2", "p3")), lib.albums, lib.people))
            assertEquals("3 people", describeSources(SourceFilter(personIds = listOf("p1", "p2", "p3")), lib.albums, lib.people))
        }
    }

    @Nested
    inner class OfflineSet {
        @Test
        fun `always shows ten slots`() = test { io ->
            val slots = FakeSlots().apply { setSlot(2, "x") }
            val m = OfflineSetModel(FakeApp(), slots, this, io)
            m.refresh()
            val s = m.state.value
            assertEquals(10, s.slots.size)
            assertEquals("x", s.slots[2].assetId)
            assertEquals(SlotStatus.READY, s.slots[2].status)
            assertNull(s.slots[0].assetId)
            assertEquals(9, s.free)
            assertEquals(1, s.ready)
        }

        @Test
        fun `picked photos fill empty slots in order`() = test { io ->
            val slots = FakeSlots().apply { setSlot(0, "x"); setSlot(2, "y") }
            val m = OfflineSetModel(FakeApp(), slots, this, io)
            m.add(listOf("a", "b", "c"))
            assertEquals(listOf("x", "a", "y", "b", "c"), slots.slots().map { it.assetId })
            assertEquals(listOf(0, 1, 2, 3, 4), slots.slots().map { it.index })
        }

        @Test
        fun `a picked photo can replace one slot`() = test { io ->
            val slots = FakeSlots().apply { setSlot(4, "old") }
            val m = OfflineSetModel(FakeApp(), slots, this, io)
            m.replace(4, "new")
            assertEquals("new", slots.slots().single().assetId)
        }

        @Test
        fun `remove clears a slot`() = test { io ->
            val slots = FakeSlots().apply { setSlot(1, "x") }
            val m = OfflineSetModel(FakeApp(), slots, this, io)
            m.remove(1)
            assertTrue(slots.slots().isEmpty())
            assertEquals(10, m.state.value.free)
        }

        @Test
        fun `fills the free slots randomly`() = test { io ->
            val slots = FakeSlots().apply { setSlot(0, "mine") }
            val m = OfflineSetModel(FakeApp(), slots, this, io)
            m.fillRandomly()
            assertEquals(10, slots.slots().size)
            assertEquals("mine", slots.slots()[0].assetId)
            assertEquals("r1", slots.slots()[1].assetId)
        }

        @Test
        fun `picked photos replace several slots`() = test { io ->
            val slots = FakeSlots().apply { setSlot(1, "a"); setSlot(3, "b"); setSlot(5, "c") }
            val m = OfflineSetModel(FakeApp(), slots, this, io)
            m.replace(listOf(1, 5), listOf("x", "y"))
            assertEquals(listOf("x", "b", "y"), slots.slots().map { it.assetId })
        }

        @Test
        fun `replacing randomly cycles through new photos, never repeating one`() = test { io ->
            val app = FakeApp().apply { random = listOf("r1", "r2", "r3", "r4", "r5") }
            val slots = FakeSlots().apply { setSlot(0, "r1"); setSlot(1, "keep") }
            val m = OfflineSetModel(app, slots, this, io)
            val seen = mutableListOf<String>()
            repeat(4) {
                m.replaceRandomly(listOf(0))
                seen += slots.map[0]!!.assetId
            }
            assertEquals(listOf("r2", "r3", "r4", "r5"), seen) // r1 was already there; nothing comes back
            assertEquals("keep", slots.map[1]!!.assetId)
        }

        @Test
        fun `when every photo has been tried, earlier ones come back`() = test { io ->
            val app = FakeApp().apply { random = listOf("r1", "r2") }
            val slots = FakeSlots().apply { setSlot(0, "r1") }
            val m = OfflineSetModel(app, slots, this, io)
            m.replaceRandomly(listOf(0))
            assertEquals("r2", slots.map[0]!!.assetId)
            m.replaceRandomly(listOf(0))
            assertEquals("r1", slots.map[0]!!.assetId)
            app.random = listOf("r1")
            m.replaceRandomly(listOf(0))
            assertEquals("r1", slots.map[0]!!.assetId)
            assertEquals("Your sources have no other photos.", m.state.value.message)
        }

        @Test
        fun `several slots are replaced randomly with different photos, not ones already in the set`() = test { io ->
            val app = FakeApp().apply { random = listOf("a", "r1", "r2", "r3") }
            val slots = FakeSlots().apply { setSlot(0, "a"); setSlot(1, "b"); setSlot(2, "c") }
            val m = OfflineSetModel(app, slots, this, io)
            m.replaceRandomly(listOf(0, 2))
            assertEquals(listOf("r1", "b", "r2"), slots.slots().map { it.assetId })
        }

        @Test
        fun `selecting photos, then acting on the selection, clears it`() = test { io ->
            val slots = FakeSlots().apply { setSlot(0, "a"); setSlot(1, "b"); setSlot(2, "c") }
            val m = OfflineSetModel(FakeApp(), slots, this, io)
            m.refresh()
            m.toggleSelected(0)
            m.toggleSelected(2)
            m.toggleSelected(7) // empty slots can't be selected
            assertEquals(setOf(0, 2), m.state.value.selected)
            m.toggleSelected(2)
            assertEquals(setOf(0), m.state.value.selected)
            m.toggleSelected(1)
            m.remove(m.state.value.selected.toList())
            assertEquals(listOf("c"), slots.slots().map { it.assetId })
            assertEquals(emptySet<Int>(), m.state.value.selected)
            m.toggleSelected(2)
            m.clearSelection()
            assertEquals(emptySet<Int>(), m.state.value.selected)
        }

        @Test
        fun `offline problems are reported, pending and missing photos explained`() = test { io ->
            val app = FakeApp().apply { randomFailure = unreachable }
            val slots = FakeSlots()
            val m = OfflineSetModel(app, slots, this, io)
            m.fillRandomly()
            assertEquals("Can't reach your Immich server.", m.state.value.message)
            slots.result = SlotResult.Pending
            m.add(listOf("a"))
            assertEquals("1 photo will download when the server is reachable.", m.state.value.message)
            slots.result = SlotResult.NotFound
            m.add(listOf("gone"))
            assertEquals("1 photo no longer exists on the server.", m.state.value.message)
            slots.failure = ImmichException.Unauthorized()
            m.add(listOf("b"))
            assertEquals("The server rejected the API key. Check it in Settings.", m.state.value.message)
            assertFalse(m.state.value.busy)
        }
    }

    @Nested
    inner class Picker {
        @Test
        fun `loads pages until the end`() = test { io ->
            val lib = FakeLibrary().apply { total = 130 }
            val m = PickerModel(lib, maxSelection = 3, alreadyChosen = emptySet(), this, io)
            m.loadMore()
            assertEquals(60, m.state.value.photos.size)
            m.loadMore()
            m.loadMore()
            assertEquals(130, m.state.value.photos.size)
            assertTrue(m.state.value.endReached)
            m.loadMore()
            assertEquals(3, lib.pageRequests.size)
        }

        @Test
        fun `switching source starts over with that filter`() = test { io ->
            val lib = FakeLibrary()
            val m = PickerModel(lib, 3, emptySet(), this, io)
            m.loadMore()
            m.setSource(PickerSource.Album("a1", "Trip"))
            assertEquals(SourceFilter(albumIds = listOf("a1")), lib.pageRequests.last().first)
            assertEquals(1, lib.pageRequests.last().second)
            assertTrue(m.state.value.photos.all { it.id.startsWith("a1-") })
            m.setSource(PickerSource.Person("p1", "Ana"))
            assertEquals(SourceFilter(personIds = listOf("p1")), lib.pageRequests.last().first)
            m.setSource(PickerSource.Favorites)
            assertEquals(SourceFilter(favoritesOnly = true), lib.pageRequests.last().first)
            m.setSource(PickerSource.All)
            assertEquals(SourceFilter(), lib.pageRequests.last().first)
        }

        @Test
        fun `selection is limited and keeps pick order`() = test { io ->
            val m = PickerModel(FakeLibrary(), maxSelection = 2, alreadyChosen = setOf("all-0"), this, io)
            m.loadMore()
            m.toggle("all-0") // already in the offline set
            m.toggle("all-5")
            m.toggle("all-3")
            m.toggle("all-4") // over the limit
            assertEquals(listOf("all-5", "all-3"), m.state.value.selected)
            assertFalse(m.state.value.canSelectMore)
            m.toggle("all-5")
            assertEquals(listOf("all-3"), m.state.value.selected)
            assertTrue(m.state.value.canSelectMore)
        }

        @Test
        fun `errors are shown and loading can resume`() = test { io ->
            val lib = FakeLibrary().apply { failure = ImmichException.Http(500) }
            val m = PickerModel(lib, 1, emptySet(), this, io)
            m.loadMore()
            assertEquals("Couldn't load photos (HTTP 500).", m.state.value.error)
            assertFalse(m.state.value.loading)
            lib.failure = null
            m.loadMore()
            assertNull(m.state.value.error)
            assertEquals(60, m.state.value.photos.size)
        }

        @Test
        fun `included photos for selected sources browse only those sources`() = test { io ->
            val pool = SourceFilter(albumIds = listOf("a1"), personIds = listOf("p1"), favoritesOnly = true)
            val m = PickerModel(FakeLibrary(), 100, emptySet(), this, io, pool)
            assertEquals(PickerSource.Album("a1", ""), m.state.value.source) // usable before names load
            m.loadSources()
            assertEquals(listOf(PickerSource.Album("a1", "Trip"), PickerSource.Person("p1", "Ana")), m.state.value.sources)
            assertEquals(PickerSource.Album("a1", "Trip"), m.state.value.source)
        }

        @Test
        fun `an album chip keeps only photos showing a chosen person, minus hidden photos and people`() = test { io ->
            val lib = FakeLibrary().apply {
                total = 20
                peopleOf = { i -> setOfNotNull("p1".takeIf { i % 2 == 0 }, "p9".takeIf { i % 4 == 0 }) }
            }
            val pool = SourceFilter(albumIds = listOf("a1"), personIds = listOf("p1"), excludedPersonIds = listOf("p9"), excludedAssetIds = listOf("a1-2"))
            val m = PickerModel(lib, 100, emptySet(), this, io, pool)
            m.loadMore()
            assertEquals(listOf("a1-6", "a1-10", "a1-14", "a1-18"), m.state.value.photos.map { it.id })
            assertTrue(lib.pageRequests.all { it.first == SourceFilter(albumIds = listOf("a1")) })
        }

        @Test
        fun `a person chip keeps only photos in a chosen album`() = test { io ->
            val lib = FakeLibrary().apply { total = 10; inAlbums = { it.endsWith("3") || it.endsWith("7") } }
            val m = PickerModel(lib, 100, emptySet(), this, io, SourceFilter(albumIds = listOf("a1"), personIds = listOf("p1")))
            m.setSource(PickerSource.Person("p1", "Ana"))
            assertEquals(listOf("p1-3", "p1-7"), m.state.value.photos.map { it.id })
            assertEquals(listOf(listOf("a1")), lib.albumLookups.distinct())
        }

        @Test
        fun `all photos minus hidden albums, and pages with nothing left are skipped`() = test { io ->
            val lib = FakeLibrary().apply { total = 200; inAlbums = { id -> id.startsWith("all-") && id.removePrefix("all-").toInt() < 150 } }
            val m = PickerModel(lib, 100, emptySet(), this, io, SourceFilter(excludedAlbumIds = listOf("screenshots")))
            m.loadMore()
            assertEquals((150 until 180).map { "all-$it" }, m.state.value.photos.map { it.id })
            assertEquals(listOf(1, 2, 3), lib.pageRequests.map { it.second }) // two empty pages skipped
            m.loadSources()
            assertEquals(PickerSource.All, m.state.value.sources.first())
        }

        @Test
        fun `offers albums and people as sources`() = test { io ->
            val m = PickerModel(FakeLibrary(), 1, emptySet(), this, io)
            m.loadSources()
            assertEquals(
                listOf(PickerSource.All, PickerSource.Favorites, PickerSource.NotInAlbum, PickerSource.Album("a2", "Family"), PickerSource.Album("a1", "Trip"), PickerSource.Person("p1", "Ana")),
                m.state.value.sources,
            )
        }

        @Test
        fun `not in an album browses all photos minus every album's photos`() = test { io ->
            val lib = FakeLibrary().apply { total = 10; inAlbums = { it.startsWith("all-") && it.removePrefix("all-").toInt() % 2 == 0 } }
            val m = PickerModel(lib, 5, emptySet(), this, io)
            m.setSource(PickerSource.NotInAlbum)
            assertEquals(listOf("all-1", "all-3", "all-5", "all-7", "all-9"), m.state.value.photos.map { it.id })
            assertEquals(SourceFilter(), lib.pageRequests.last().first)
            assertEquals(listOf(listOf("a1", "a2")), lib.albumLookups)
        }

        @Test
        fun `included photos with not in an album - a person chip keeps photos outside every album`() = test { io ->
            val lib = FakeLibrary().apply { total = 10; inAlbums = { it.startsWith("p1-") && it.removePrefix("p1-").toInt() < 5 } }
            val pool = SourceFilter(personIds = listOf("p1"), notInAlbum = true)
            val m = PickerModel(lib, 100, emptySet(), this, io, pool)
            assertEquals(listOf(PickerSource.NotInAlbum, PickerSource.Person("p1", "")), m.state.value.sources)
            m.setSource(PickerSource.Person("p1", "Ana"))
            assertEquals((5..9).map { "p1-$it" }, m.state.value.photos.map { it.id })
        }
    }

    @Nested
    inner class Home {
        @Test
        fun `lists problems in order of importance`() {
            assertEquals(emptyList<Problem>(), problems(snapshotOf()))
            assertEquals(listOf(Problem.NOT_CONFIGURED), problems(snapshotOf(configured = false, serviceOn = false)))
            assertEquals(
                listOf(Problem.SERVICE_OFF, Problem.BATTERY_OPTIMIZED, Problem.NO_OFFLINE_SET),
                problems(snapshotOf(serviceOn = false, batteryExempt = false, offlineReady = 0)),
            )
            assertEquals(listOf(Problem.KEY_REJECTED), problems(snapshotOf(lastRefillFailure = FailReason.AUTH)))
            assertEquals(listOf(Problem.UNREACHABLE), problems(snapshotOf(lastRefillFailure = FailReason.UNREACHABLE)))
            assertEquals(listOf(Problem.LOCAL_NETWORK), problems(snapshotOf(lastRefillFailure = FailReason.UNREACHABLE, localNetworkAllowed = false)))
            assertEquals(emptyList<Problem>(), problems(snapshotOf(localNetworkAllowed = false))) // only matters when it blocks
            assertEquals(listOf(Problem.NO_PHOTOS), problems(snapshotOf(lastRefillFailure = FailReason.NO_PHOTOS)))
            assertEquals(listOf(Problem.STORAGE_FULL), problems(snapshotOf(lastRefillFailure = FailReason.STORAGE_FULL)))
        }

        @Test
        fun `small selections explain themselves instead of preparing forever`() {
            assertNull(upNextNote(snapshotOf()))
            assertEquals("No photos in your sources", upNextNote(snapshotOf(poolSize = 0)))
            assertEquals("No photos in your sources", upNextNote(snapshotOf(lastRefillFailure = FailReason.NO_PHOTOS)))
            assertEquals("Your sources have only 1 photo", upNextNote(snapshotOf(poolSize = 1)))
            assertEquals("Your sources have only 2 photos, so they take turns", upNextNote(snapshotOf(poolSize = 2)))
        }

        @Test
        fun `the only photo in the sources can't be hidden`() {
            assertTrue(canHideNowShowing(snapshotOf()))
            assertTrue(canHideNowShowing(snapshotOf(poolSize = 2)))
            assertFalse(canHideNowShowing(snapshotOf(poolSize = 1)))
            assertFalse(canHideNowShowing(snapshotOf(poolSize = 0)))
        }

        @Test
        fun `refresh reads a snapshot and actions go to the app`() = test { io ->
            val app = FakeApp().apply { snap = snapshotOf(queued = 1) }
            val m = HomeModel(app, this, io)
            m.refresh()
            assertEquals(1, m.state.value!!.snapshot.queued)
            m.swapNow()
            assertEquals(1, app.swaps)
            assertEquals(listOf("shown-1"), m.state.value!!.snapshot.nowShowing)
            m.hide("shown-1")
            assertEquals(listOf("shown-1"), app.config.value.filter.excludedAssetIds)
            assertEquals(2, app.swaps) // hidden photo replaced right away
        }

        @Test
        fun `turning off stops the app and puts back what the user chose`() = test { io ->
            val app = FakeApp()
            val m = HomeModel(app, this, io)
            var pickers = 0
            m.turnOff(AfterTurnOff.KEEP_PHOTO) { pickers++ }
            m.turnOff(AfterTurnOff.PHONE_DEFAULT) { pickers++ }
            m.turnOff(AfterTurnOff.CHOOSE) { pickers++ }
            assertEquals(listOf(false, true, false), app.turnedOff)
            assertEquals(1, pickers) // only "choose" opens the wallpaper picker, after stopping
            assertFalse(m.state.value!!.snapshot.serviceOn)
        }
    }

    @Nested
    inner class SettingsScreen {
        @Test
        fun `changes go straight to the config`() = test { io ->
            val app = FakeApp()
            val m = SettingsModel(app)
            m.setApplyTarget(ApplyTarget.LOCK)
            m.setSideBySide(true)
            m.setFaceAware(false)
            m.setWifiOnly(true)
            m.setCooldownSeconds(5)
            val s = app.config.value.settings
            assertEquals(ApplyTarget.LOCK, s.applyTarget)
            assertTrue(s.sideBySide)
            assertFalse(s.faceAwareCrop)
            assertTrue(s.wifiOnlyRefill)
            assertEquals(5_000, s.cooldownMillis)
            assertTrue(m.sideBySideAvailable)
            m.retryDisabledFeatures()
            assertEquals(1, app.resets)
        }
    }

    @Nested
    inner class Onboarding {
        @Test
        fun `walks through the steps and finishes once`() = test { io ->
            val app = FakeApp()
            val m = OnboardingModel(app)
            assertEquals(OnboardingStep.SERVER, m.step.value)
            assertFalse(m.back())
            m.next()
            assertEquals(OnboardingStep.SOURCES, m.step.value)
            m.next()
            assertEquals(OnboardingStep.OFFLINE_SET, m.step.value)
            assertTrue(m.back())
            assertEquals(OnboardingStep.SOURCES, m.step.value)
            m.next()
            m.next()
            assertEquals(OnboardingStep.BACKGROUND, m.step.value)
            assertFalse(app.config.value.onboarded)
            m.next()
            assertTrue(app.config.value.onboarded)
            assertEquals(OnboardingStep.BACKGROUND, m.step.value)
            assertEquals(4, OnboardingStep.entries.size)
            assertEquals(2, OnboardingStep.SOURCES.number)
        }
    }

    @Nested
    inner class Navigation {
        @Test
        fun `tabs reset the stack and back pops pushed screens`() {
            val nav = Navigator()
            assertEquals(Destination.Home, nav.current)
            nav.push(Destination.Log)
            assertEquals(Destination.Log, nav.current)
            assertEquals(Tab.HOME, nav.tab)
            assertTrue(nav.back())
            assertEquals(Destination.Home, nav.current)
            assertFalse(nav.back())
            nav.select(Tab.OFFLINE_SET)
            nav.push(Destination.Picker(slots = listOf(3)))
            assertEquals(Tab.OFFLINE_SET, nav.tab)
            nav.select(Tab.SETTINGS)
            assertEquals(listOf<Destination>(Destination.Settings), nav.stack.value)
            assertTrue(nav.back()) // from another tab, back goes home
            assertEquals(Destination.Home, nav.current)
            assertEquals(Destination.Sources, Tab.SOURCES.root)
        }
    }
}
