package com.wiggyserver.immichwallpaper.core

import java.time.LocalDateTime

/** A face box in fractions of the (orientation-corrected) image, optionally tied to a person. */
data class Face(val box: Box, val personId: String? = null)

/**
 * A photo from Immich. [size] is the orientation-corrected size (Immich's top-level width/height,
 * as measured on Immich 3.2). [takenAt] is the local capture time.
 */
data class Asset(
    val id: String,
    val size: Size,
    val takenAt: LocalDateTime?,
    val personIds: Set<String> = emptySet(),
    val faces: List<Face> = emptyList(),
    /** Marked as a favorite in Immich (checked when sources are favorites only). */
    val favorite: Boolean = false,
    /** Archived in Immich (hidden from its timeline): left out unless it's in a chosen album. */
    val archived: Boolean = false,
)
