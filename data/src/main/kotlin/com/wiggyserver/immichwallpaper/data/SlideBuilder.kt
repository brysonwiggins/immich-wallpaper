package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Layout
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.RenderPlanner
import com.wiggyserver.immichwallpaper.core.ScreenJob
import com.wiggyserver.immichwallpaper.core.SlideFile
import com.wiggyserver.immichwallpaper.core.SlidePlan
import java.io.File

/** Shared by the queue and the offline set: add faces, plan renders, write one file per set call. */
internal class SlideBuilder(private val api: ImmichApi, private val renderer: SlideRenderer) {

    /** Faces for the consumed assets (only when face-aware). A failed lookup just means "no faces". */
    fun withFaces(plan: SlidePlan, faceAware: Boolean): SlidePlan {
        if (!faceAware) return plan
        val enriched = plan.consumed.associate { asset ->
            asset.id to asset.copy(faces = runCatching { api.faces(asset.id) }.getOrDefault(emptyList()))
        }
        fun Asset.swap() = enriched[id] ?: this
        return SlidePlan(
            plan.consumed.map { it.swap() },
            plan.layouts.mapValues { (_, layout) ->
                when (layout) {
                    is Layout.Single -> Layout.Single(layout.asset.swap())
                    is Layout.Pair -> Layout.Pair(layout.left.swap(), layout.right.swap())
                }
            },
        )
    }

    /** Renders every job into [files]; on failure deletes what it wrote and rethrows. */
    fun render(id: String, plan: SlidePlan, jobs: List<ScreenJob>, sources: Map<String, File>, files: FileStore, faceAware: Boolean): PreparedSlide {
        val written = mutableListOf<String>()
        try {
            val renders = RenderPlanner(faceAware = faceAware).plan(plan, jobs)
            val slideFiles = renders.mapIndexed { i, r ->
                val name = "slide-$id-$i.jpg"
                written += name
                files.write(name) { out -> renderer.render(r, sources, out) }
                SlideFile(r.job.which, r.job.method, name, r.cropHints)
            }
            return PreparedSlide(id, plan.consumed.map { it.id }, slideFiles)
        } catch (e: Exception) {
            written.forEach(files::delete)
            throw e
        }
    }
}
