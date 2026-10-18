package com.wiggyserver.immichwallpaper.core

import java.time.LocalDateTime

internal fun portrait(id: String, takenAt: String? = null, people: Set<String> = emptySet(), faces: List<Face> = emptyList()) =
    Asset(id, Size(3000, 4000), takenAt?.let(LocalDateTime::parse), people, faces)

internal fun landscape(id: String, takenAt: String? = null, people: Set<String> = emptySet()) =
    Asset(id, Size(4000, 3000), takenAt?.let(LocalDateTime::parse), people)

internal fun square(id: String) = Asset(id, Size(2000, 2000), null)
