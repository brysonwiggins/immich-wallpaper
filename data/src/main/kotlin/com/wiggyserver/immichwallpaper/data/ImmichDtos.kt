package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Box
import com.wiggyserver.immichwallpaper.core.Face
import com.wiggyserver.immichwallpaper.core.Size
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.OffsetDateTime

// Only the fields the app uses; unknown keys are ignored (Json { ignoreUnknownKeys = true }).

@Serializable
internal data class ServerVersionDto(val major: Int, val minor: Int, val patch: Int)

@Serializable
internal data class ExifDto(
    val exifImageWidth: Int? = null,
    val exifImageHeight: Int? = null,
    val orientation: String? = null,
)

@Serializable
internal data class PersonRefDto(val id: String)

@Serializable
internal data class AssetDto(
    val id: String,
    val type: String = "IMAGE",
    val width: Int? = null,
    val height: Int? = null,
    val localDateTime: String? = null,
    val updatedAt: String? = null,
    val isTrashed: Boolean = false,
    val isFavorite: Boolean = false,
    /** Immich 1.133+: "timeline", "archive", "hidden" or "locked". */
    val visibility: String? = null,
    /** Older servers' archive flag. */
    val isArchived: Boolean = false,
    val exifInfo: ExifDto? = null,
    val people: List<PersonRefDto> = emptyList(),
) {
    /** Null for non-images, trashed assets and assets without usable dimensions. */
    fun toAsset(): Asset? {
        if (type != "IMAGE" || isTrashed) return null
        val size = size() ?: return null
        return Asset(id, size, localDateTime?.let(::parseLocal), people.map { it.id }.toSet(), favorite = isFavorite, archived = isArchived || visibility == "archive")
    }

    /** Immich's top-level width/height are orientation-corrected; EXIF dims are raw and need swapping for 5–8. */
    private fun size(): Size? {
        if (width != null && height != null && width > 0 && height > 0) return Size(width, height)
        val w = exifInfo?.exifImageWidth ?: return null
        val h = exifInfo.exifImageHeight ?: return null
        return if (exifInfo.orientation in ROTATED_ORIENTATIONS) Size(h, w) else Size(w, h)
    }
}

/** EXIF orientations 5–8 rotate the image by 90°, so raw EXIF width/height are swapped. */
private val ROTATED_ORIENTATIONS = setOf("5", "6", "7", "8")

/** Immich stores local capture time with a "Z" suffix; it is wall-clock time, not UTC. */
private fun parseLocal(s: String): LocalDateTime? = runCatching { OffsetDateTime.parse(s).toLocalDateTime() }
    .recoverCatching { LocalDateTime.parse(s) }.getOrNull()

@Serializable
internal data class FacePersonDto(val id: String)

@Serializable
internal data class FaceDto(
    val boundingBoxX1: Int,
    val boundingBoxY1: Int,
    val boundingBoxX2: Int,
    val boundingBoxY2: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val person: FacePersonDto? = null,
) {
    fun toFace(): Face? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val w = imageWidth.toDouble()
        val h = imageHeight.toDouble()
        return Face(Box(boundingBoxX1 / w, boundingBoxY1 / h, boundingBoxX2 / w, boundingBoxY2 / h), person?.id)
    }
}

@Serializable
internal data class AlbumDto(val id: String, val albumName: String = "", val assetCount: Int = 0, val albumThumbnailAssetId: String? = null)

@Serializable
internal data class MetadataAssetsDto(val items: List<AssetDto> = emptyList(), val nextPage: String? = null)

@Serializable
internal data class MetadataSearchDto(val assets: MetadataAssetsDto = MetadataAssetsDto())

@Serializable
internal data class PersonDto(val id: String, val name: String = "", val isHidden: Boolean = false)

@Serializable
internal data class PeoplePageDto(val people: List<PersonDto> = emptyList())
