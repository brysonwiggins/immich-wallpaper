package com.wiggyserver.immichwallpaper.core

/** What one viewport shows. */
sealed interface Layout {
    data class Single(val asset: Asset) : Layout

    /** Two portraits side by side; [left] is the primary asset. */
    data class Pair(val left: Asset, val right: Asset) : Layout
}

/** One slide: a layout for every viewport, built from the [consumed] assets (in order). */
data class SlidePlan(val consumed: List<Asset>, val layouts: Map<Viewport, Layout>)

/**
 * Chooses single vs. pair per viewport:
 * - Every viewport shows the first candidate alone, except
 * - a landscape-capable viewport on a screen that supports side-by-side, when the setting is on and
 *   the first candidate is a portrait with a portrait partner available: that viewport shows the pair.
 * A landscape first candidate is never paired; remaining portraits wait for a later slide.
 */
class LayoutPlanner(
    private val classifier: AspectClassifier = AspectClassifier(),
    private val pairer: Pairer = Pairer(classifier),
) {
    fun plan(candidates: List<Asset>, viewports: List<Viewport>, sideBySide: Boolean): SlidePlan? {
        val primary = candidates.firstOrNull() ?: return null
        val pairable = viewports.filter { sideBySide && canPair(it) }
        val partner = if (pairable.isNotEmpty() && classifier.classify(primary.size) == AspectClass.PORTRAIT) {
            pairer.bestPartner(primary, candidates.drop(1))
        } else {
            null
        }
        val layouts = viewports.associateWith { viewport ->
            if (partner != null && viewport in pairable) Layout.Pair(primary, partner) else Layout.Single(primary)
        }
        return SlidePlan(listOfNotNull(primary, partner), layouts)
    }

    private fun canPair(viewport: Viewport) =
        viewport.screen.supportsSideBySide() && viewport.size.width >= viewport.size.height
}
