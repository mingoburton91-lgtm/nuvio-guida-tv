package com.nuvio.tv.data.mapper

import com.nuvio.tv.data.remote.dto.MetaPreviewDto
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun MetaPreviewDto.toDomainOrNull(catalogType: String, sourceAddonBaseUrl: String? = null): MetaPreview? {
    val resolvedId = id?.takeIf { it.isNotBlank() } ?: return null
    val resolvedName = name?.takeIf { it.isNotBlank() } ?: return null
    val resolvedType = type?.takeIf { it.isNotBlank() } ?: catalogType
    val tvVooDescription = sourceAddonBaseUrl
        ?.takeIf { it.contains("tvvoo.hayd.uk", ignoreCase = true) }
        ?.let {
            val now = Instant.now()
            val programmes = videos.orEmpty().mapNotNull { video ->
                val start = runCatching { Instant.parse(video.startTime) }.getOrNull()
                val end = runCatching { Instant.parse(video.endTime) }.getOrNull()
                if (start != null && end != null) Triple(video, start, end) else null
            }.sortedBy { it.second }
            val currentIndex = programmes.indexOfFirst { (_, start, end) ->
                !start.isAfter(now) && end.isAfter(now)
            }
            if (currentIndex >= 0) {
                val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)
                    .withZone(ZoneId.of("Europe/Rome"))
                val current = programmes[currentIndex]
                val next = programmes.getOrNull(currentIndex + 1)
                buildString {
                    append("● IN ONDA ")
                    append(fmt.format(current.second)).append("–").append(fmt.format(current.third))
                    append(" · ").append(current.first.title ?: current.first.name ?: "Programma")
                    if (next != null) {
                        append("\nA seguire ")
                        append(fmt.format(next.second)).append("–").append(fmt.format(next.third))
                        append(" · ").append(next.first.title ?: next.first.name ?: "Programma")
                    }
                }
            } else null
        }

    return MetaPreview(
        id = resolvedId,
        type = ContentType.fromString(resolvedType),
        rawType = resolvedType,
        name = resolvedName,
        poster = poster,
        posterShape = PosterShape.fromString(posterShape),
        background = background,
        logo = logo,
        description = tvVooDescription ?: description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating?.toFloatOrNull(),
        genres = genres ?: emptyList(),
        runtime = runtime,
        status = status?.trim()?.takeIf { it.isNotBlank() },
        released = released,
        country = country,
        imdbId = imdbId,
        slug = slug,
        landscapePoster = landscapePoster,
        rawPosterUrl = rawPosterUrl,
        director = coerceStringList(director),
        writer = coerceStringList(writer).ifEmpty { coerceStringList(writers) },
        links = links?.mapNotNull { it.toDomain() } ?: emptyList(),
        behaviorHints = mapBehaviorHints(behaviorHints),
        trailers = mapTrailers(trailers, trailerStreams),
        trailerYtIds = collectTrailerYtIds(trailers, trailerStreams),
        sourceAddonBaseUrl = sourceAddonBaseUrl
    )
}
