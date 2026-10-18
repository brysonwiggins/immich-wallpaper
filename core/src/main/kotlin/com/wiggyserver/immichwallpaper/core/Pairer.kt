package com.wiggyserver.immichwallpaper.core

import java.time.Duration

/**
 * Picks a side-by-side partner for a portrait: same day > same person > same trip (within
 * [tripWindow]) > any portrait. Scores add up, so "same day and same person" wins over either.
 * Ties keep candidate order (the server's random order).
 */
class Pairer(
    private val classifier: AspectClassifier = AspectClassifier(),
    private val tripWindow: Duration = Duration.ofDays(3),
) {
    fun bestPartner(asset: Asset, candidates: List<Asset>): Asset? = candidates
        .filter { it.id != asset.id && classifier.classify(it.size) == AspectClass.PORTRAIT }
        .withIndex()
        .maxWithOrNull(compareBy<IndexedValue<Asset>> { score(asset, it.value) }.thenByDescending { it.index })
        ?.value

    internal fun score(a: Asset, b: Asset): Int {
        var score = 0
        val ta = a.takenAt
        val tb = b.takenAt
        if (ta != null && tb != null) {
            if (ta.toLocalDate() == tb.toLocalDate()) {
                score += SAME_DAY
            } else if (Duration.between(ta, tb).abs() <= tripWindow) {
                score += SAME_TRIP
            }
        }
        if (a.personIds.intersect(b.personIds).isNotEmpty()) score += SAME_PERSON
        return score
    }

    private companion object {
        const val SAME_DAY = 4
        const val SAME_PERSON = 2
        const val SAME_TRIP = 1
    }
}
