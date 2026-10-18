package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PairerTest {
    private val pairer = Pairer()
    private val base = portrait("base", "2024-05-01T10:00", people = setOf("alex"))

    @Test
    fun `prefers a partner from the same day`() {
        val sameDay = portrait("sameDay", "2024-05-01T18:00")
        val samePerson = portrait("samePerson", "2023-01-01T10:00", people = setOf("alex"))
        assertEquals(sameDay, pairer.bestPartner(base, listOf(samePerson, sameDay)))
    }

    @Test
    fun `then a partner showing the same person`() {
        val samePerson = portrait("samePerson", "2023-01-01T10:00", people = setOf("alex"))
        val sameTrip = portrait("sameTrip", "2024-05-03T10:00")
        assertEquals(samePerson, pairer.bestPartner(base, listOf(sameTrip, samePerson)))
    }

    @Test
    fun `then a partner from the same trip (within 3 days)`() {
        val sameTrip = portrait("sameTrip", "2024-04-29T09:00")
        val any = portrait("any", "2019-01-01T00:00")
        assertEquals(sameTrip, pairer.bestPartner(base, listOf(any, sameTrip)))
    }

    @Test
    fun `falls back to the first portrait in candidate order`() {
        val a = portrait("a", "2019-01-01T00:00")
        val b = portrait("b", null)
        assertEquals(a, pairer.bestPartner(base, listOf(a, b)))
    }

    @Test
    fun `same day plus same person beats same day alone`() {
        val sameDay = portrait("sameDay", "2024-05-01T11:00")
        val both = portrait("both", "2024-05-01T12:00", people = setOf("alex"))
        assertEquals(both, pairer.bestPartner(base, listOf(sameDay, both)))
    }

    @Test
    fun `ignores landscapes, squares and the asset itself`() {
        assertNull(pairer.bestPartner(base, listOf(base, landscape("l", "2024-05-01T10:00"), square("s"))))
    }

    @Test
    fun `no candidates means no partner`() = assertNull(pairer.bestPartner(base, emptyList()))
}
