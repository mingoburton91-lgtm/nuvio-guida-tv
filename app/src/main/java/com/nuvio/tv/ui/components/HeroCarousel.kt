package com.nuvio.tv.ui.components

import com.nuvio.tv.ui.theme.NuvioTheme

import android.graphics.ColorSpace
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.util.StableList
import com.nuvio.tv.ui.util.recompositionHighlighter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.colorSpace
import coil3.request.crossfade
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.formatHeroRuntime
import com.nuvio.tv.ui.util.LocalRecompositionHighlighterEnabled
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

private const val AUTO_ADVANCE_INTERVAL_MS = 10000L
private val YEAR_REGEX = Regex("""\b\d{4}\b""")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroCarousel(
    items: StableList<MetaPreview>,
    onItemClick: (MetaPreview) -> Unit,
    onItemFocus: (MetaPreview) -> Unit = {},
    onActiveItemChanged: (MetaPreview) -> Unit = {},
    focusRequester: FocusRequester? = null,
    showImdbRatings: Boolean = true,
    showBackdrop: Boolean = true,
    fullWidth: Dp = Dp.Unspecified,
    initialActiveIndex: Int = 0,
    mdbListShowOnHero: Boolean = false,
    mdbListRatingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentOnItemFocus by rememberUpdatedState(onItemFocus)
    val currentOnActiveItemChanged by rememberUpdatedState(onActiveItemChanged)
    var activeIndex by remember { mutableIntStateOf(initialActiveIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var isFocused by remember { mutableStateOf(false) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LaunchedEffect(activeIndex, isFocused) {
        if (!isFocused) return@LaunchedEffect
        items.getOrNull(activeIndex)?.let { currentOnItemFocus(it) }
    }

    LaunchedEffect(activeIndex, items) {
        items.getOrNull(activeIndex)?.let { currentOnActiveItemChanged(it) }
    }

    // Auto-advance when not focused — delay first advance to 20s so initial GPU load settles
    LaunchedEffect(isFocused, items.size) {
        if (items.size <= 1) return@LaunchedEffect
        delay(AUTO_ADVANCE_INTERVAL_MS * 2) // 20s before first advance
        while (true) {
            delay(AUTO_ADVANCE_INTERVAL_MS)
            if (!isFocused) {
                activeIndex = (activeIndex + 1) % items.size
            }
        }
    }

    Box(
        modifier = modifier
            .then(
                if (fullWidth != Dp.Unspecified)
                    Modifier.requiredWidth(fullWidth)
                else
                    Modifier.fillMaxWidth()
            )
            .height(400.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused = it.hasFocus || it.isFocused
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (isRtl) {
                                if (activeIndex < items.size - 1) { activeIndex++; true } else false
                            } else {
                                if (activeIndex > 0) { activeIndex--; true } else false
                            }
                        }
                        Key.DirectionRight -> {
                            if (isRtl) {
                                if (activeIndex > 0) { activeIndex--; true } else false
                            } else {
                                if (activeIndex < items.size - 1) { activeIndex++; true } else false
                            }
                        }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    currentOnItemClick(items[activeIndex])
                    true
                } else {
                    false
                }
            }
    ) {
        // Crossfade between slides
        Crossfade(
            targetState = activeIndex,
            animationSpec = tween(300),
            label = "heroSlide"
        ) { index ->
            val item = items.getOrNull(index) ?: return@Crossfade
            HeroCarouselSlide(
                item = item,
                showImdbRatings = showImdbRatings,
                showBackdrop = showBackdrop,
                mdbListShowOnHero = mdbListShowOnHero,
                mdbListRatingOrder = mdbListRatingOrder
            )
        }

        // Indicator dots — optimized to minimize recompositions and layout passes
        val focusRingBrush = NuvioTheme.focusRing.brush()
        val dotColorInactive = remember { Color.White.copy(alpha = 0.3f) }
        val dotShape = remember { RoundedCornerShape(3.dp) }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = NuvioTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            repeat(items.size) { index ->
                val isActive = index == activeIndex
                val useGradient = isActive
                val dotColor = when {
                    isActive -> null // use gradient brush
                    else -> dotColorInactive
                }
                val dotWidth = when {
                    isActive -> NuvioTheme.spacing.xxl
                    else -> NuvioTheme.spacing.md
                }
                val dotHeight = if (isActive) 6.dp else NuvioTheme.spacing.xs
                
                Box(
                    modifier = Modifier
                        .size(width = dotWidth, height = dotHeight)
                        .clip(dotShape)
                        .then(
                            if (useGradient) Modifier.background(focusRingBrush)
                            else Modifier.background(dotColor!!)
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroCarouselSlide(
    item: MetaPreview,
    showImdbRatings: Boolean,
    showBackdrop: Boolean,
    mdbListShowOnHero: Boolean = false,
    mdbListRatingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER
) {
    val highlighterEnabled = LocalRecompositionHighlighterEnabled.current
    var epgDescription by remember(item.id) { mutableStateOf<String?>(null) }
    val isTvVooItem = item.sourceAddonBaseUrl.orEmpty().contains("tvvoo.hayd.uk", ignoreCase = true) ||
        item.id.contains("vavoo", ignoreCase = true) || item.id.contains("tvvoo", ignoreCase = true)
    LaunchedEffect(item.id, item.name, isTvVooItem) {
        epgDescription = if (isTvVooItem) resolveHeroEpg(item.name) else null
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val logoRequestWidthPx = remember(density) {
        with(density) { 220.dp.roundToPx() }.coerceAtLeast(1)
    }
    val logoRequestHeightPx = remember(density) { with(density) { 100.dp.roundToPx() }.coerceAtLeast(1) }

    val logoModel = remember(context, item.logo, logoRequestWidthPx, logoRequestHeightPx) {
        item.logo?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .size(width = logoRequestWidthPx, height = logoRequestHeightPx)
                .build()
        }
    }
    var logoLoadFailed by remember(item.logo) { mutableStateOf(false) }
    val showLogo = !item.logo.isNullOrBlank() && !logoLoadFailed
    val contentTypeText = remember(context, item.apiType) {
        localizedContentType(context, item.apiType).takeIf { it.isNotBlank() }
    }
    val primaryGenreText = remember(context, item.genres) {
        item.genres.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { localizedGenreLabel(context, it) }
    }
    val runtimeText = remember(item.runtime) { formatHeroRuntime(item.runtime) }
    val releaseYear = remember(item.releaseInfo) {
        item.releaseInfo?.let { releaseInfo ->
            YEAR_REGEX.find(releaseInfo)?.value ?: releaseInfo.split("-").firstOrNull()
        }?.trim()?.takeIf { it.isNotEmpty() }
    }
    val leadingMetaText = remember(contentTypeText, primaryGenreText) {
        listOfNotNull(contentTypeText, primaryGenreText).joinToString(separator = " • ")
    }
    val trailingMetadata = remember(runtimeText, releaseYear) {
        listOfNotNull(runtimeText, releaseYear)
    }
    val ratingText = remember(item.imdbRating, showImdbRatings) {
        item.imdbRating
            ?.takeIf { showImdbRatings }
            ?.let { String.format("%.1f", it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (highlighterEnabled) Modifier.recompositionHighlighter() else Modifier)
    ) {
        if (showBackdrop) {
            HeroCarouselBackdrop(
                item = item,
                fullPage = false,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = NuvioTheme.spacing.xxxl, bottom = NuvioTheme.spacing.xxxl, end = NuvioTheme.spacing.xxxl)
                .fillMaxWidth(0.42f),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            if (showLogo) {
                AsyncImage(
                    model = logoModel,
                    contentDescription = item.name,
                    onError = { logoLoadFailed = true },
                    modifier = Modifier
                        .height(100.dp)
                        .widthIn(min = 100.dp, max = 220.dp)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = NuvioTheme.colors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (leadingMetaText.isNotBlank() || trailingMetadata.isNotEmpty() || ratingText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasTrailingMeta = trailingMetadata.isNotEmpty() || ratingText != null
                    if (leadingMetaText.isNotBlank()) {
                        Text(
                            text = leadingMetaText,
                            style = MaterialTheme.typography.labelMedium,
                            color = NuvioTheme.colors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (hasTrailingMeta) {
                                Modifier.weight(1f, fill = false)
                            } else {
                                Modifier
                            }
                        )
                    }
                    if (hasTrailingMeta) {
                        if (leadingMetaText.isNotBlank()) HeroCarouselMetaDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
                        ) {
                            trailingMetadata.forEachIndexed { index, value ->
                                if (index > 0) HeroCarouselMetaDivider()
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NuvioTheme.colors.TextSecondary,
                                    maxLines = 1
                                )
                            }
                            if (ratingText != null) {
                                val mdbRatings = item.mdbListRatings
                                if (mdbListShowOnHero && mdbRatings != null && !mdbRatings.isEmpty()) {
                                } else {
                                    if (trailingMetadata.isNotEmpty()) HeroCarouselMetaDivider()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
                                    ) {
                                        ImdbRatingSourceLabel(
                                            logoModifier = Modifier.size(NuvioTheme.spacing.xl),
                                            textStyle = MaterialTheme.typography.labelMedium,
                                            textColor = NuvioTheme.colors.TextSecondary
                                        )
                                        Text(
                                            text = ratingText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NuvioTheme.colors.TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (mdbListShowOnHero && item.mdbListRatings != null && !item.mdbListRatings.isEmpty()) {
                MDBListRatingsRow(
                    ratings = item.mdbListRatings,
                    order = mdbListRatingOrder,
                    maxItems = 6
                )
            }

            (epgDescription ?: item.description)?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = description.contentTextDirection()
                    ),
                    color = NuvioTheme.colors.TextPrimary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HeroCarouselMetaDivider() {
    Box(
        modifier = Modifier
            .size(NuvioTheme.spacing.xs.coerceAtLeast(NuvioTheme.spacing.xxs))
            .clip(RoundedCornerShape(percent = 50))
            .background(NuvioTheme.colors.TextTertiary.copy(alpha = 0.78f))
    )
}

@Composable
internal fun HeroCarouselBackdrop(
    item: MetaPreview,
    fullPage: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val requestWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    }
    val requestHeightPx = remember(configuration.screenHeightDp, density, fullPage) {
        val height = if (fullPage) configuration.screenHeightDp.dp else 400.dp
        with(density) { height.roundToPx() }.coerceAtLeast(1)
    }
    val backdropUrl = item.backdropUrl
    val backgroundModel = remember(context, backdropUrl, requestWidthPx, requestHeightPx, fullPage) {
        ImageRequest.Builder(context)
            .data(backdropUrl)
            .crossfade(false)
            .size(width = requestWidthPx, height = requestHeightPx)
            .apply {
                if (fullPage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                }
            }
            .build()
    }
    val bgColor = NuvioTheme.colors.Background

    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
            val bottomStartFraction = if (fullPage) 0.55f else 0.30f
            val leftEndFraction = if (fullPage) 0.66f else 0.72f
            val bottomStartY = size.height * bottomStartFraction
            val leftEndX = size.width * leftEndFraction
            val bottomGradient = Brush.verticalGradient(
                colorStops = if (fullPage) {
                    arrayOf(
                        0.0f to Color.Transparent,
                        0.4222f to bgColor.copy(alpha = 0.32f),
                        0.7778f to bgColor.copy(alpha = 0.62f),
                        1.0f to bgColor.copy(alpha = 0.78f)
                    )
                } else {
                    arrayOf(
                        0.0f to Color.Transparent,
                        0.4286f to bgColor.copy(alpha = 0.5f),
                        0.7143f to bgColor.copy(alpha = 0.85f),
                        1.0f to bgColor
                    )
                },
                startY = bottomStartY,
                endY = size.height
            )
            val leftGradient = Brush.horizontalGradient(
                colorStops = if (fullPage) {
                    arrayOf(
                        0.0f to bgColor.copy(alpha = 0.98f),
                        0.2424f to bgColor.copy(alpha = 0.88f),
                        0.5152f to bgColor.copy(alpha = 0.56f),
                        0.7879f to bgColor.copy(alpha = 0.20f),
                        1.0f to Color.Transparent
                    )
                } else {
                    arrayOf(
                        0.0f to bgColor.copy(alpha = 0.98f),
                        0.2222f to bgColor.copy(alpha = 0.88f),
                        0.4722f to bgColor.copy(alpha = 0.56f),
                        0.7778f to bgColor.copy(alpha = 0.20f),
                        1.0f to Color.Transparent
                    )
                },
                startX = 0f,
                endX = leftEndX
            )
            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = bottomGradient,
                    topLeft = Offset(0f, bottomStartY),
                    size = Size(size.width, size.height - bottomStartY)
                )
                drawRect(
                    brush = leftGradient,
                    size = Size(leftEndX, size.height)
                )
            }
        }
    ) {
        AsyncImage(
            model = backgroundModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter
        )
    }
}


private data class HeroEpgProgramme(
    val title: String,
    val description: String?,
    val start: Instant,
    val end: Instant
)

private val HERO_EPG_URLS = listOf(
    "https://iptv-org.github.io/epg/guides/it/guidatv.sky.it.epg.xml",
    "https://iptv-org.github.io/epg/guides/it/mediaset.it.epg.xml"
)

private suspend fun resolveHeroEpg(channelName: String): String? = withContext(Dispatchers.IO) {
    val wanted = normalizeHeroEpgName(channelName)
    val now = Instant.now()
    for (url in HERO_EPG_URLS) {
        val programmes = runCatching { readHeroEpg(url, wanted) }.getOrDefault(emptyList())
        val index = programmes.indexOfFirst { !it.start.isAfter(now) && it.end.isAfter(now) }
        if (index >= 0) {
            val current = programmes[index]
            val next = programmes.getOrNull(index + 1)
            val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN).withZone(ZoneId.of("Europe/Rome"))
            return@withContext buildString {
                append("● IN ONDA ").append(fmt.format(current.start)).append("–").append(fmt.format(current.end))
                    .append(" · ").append(current.title)
                current.description?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
                if (next != null) {
                    append("\nA seguire ").append(fmt.format(next.start)).append("–").append(fmt.format(next.end))
                        .append(" · ").append(next.title)
                    next.description?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
                }
            }
        }
    }
    null
}

private fun readHeroEpg(address: String, wantedName: String): List<HeroEpgProgramme> {
    fun connection() = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 20000
    }
    val ids = HashSet<String>()
    var c = connection()
    try {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(c.inputStream, "UTF-8")
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.name == "channel") {
                val id = p.getAttributeValue(null, "id").orEmpty()
                val names = ArrayList<String>()
                var inner = p.next()
                while (!(inner == XmlPullParser.END_TAG && p.name == "channel")) {
                    if (inner == XmlPullParser.START_TAG && p.name == "display-name") names += p.nextText()
                    inner = p.next()
                }
                if (names.any { normalizeHeroEpgName(it) == wantedName } ||
                    normalizeHeroEpgName(id.removeSuffix(".it")) == wantedName ||
                    normalizeHeroEpgName(id) == wantedName) ids += id
            }
            event = p.next()
        }
    } finally { c.disconnect() }
    if (ids.isEmpty()) return emptyList()

    val out = ArrayList<HeroEpgProgramme>()
    c = connection()
    try {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(c.inputStream, "UTF-8")
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.name == "programme") {
                val channel = p.getAttributeValue(null, "channel").orEmpty()
                val start = parseHeroEpgTime(p.getAttributeValue(null, "start"))
                val end = parseHeroEpgTime(p.getAttributeValue(null, "stop"))
                var title = "Programma"
                var desc: String? = null
                var inner = p.next()
                while (!(inner == XmlPullParser.END_TAG && p.name == "programme")) {
                    if (inner == XmlPullParser.START_TAG && p.name == "title") title = p.nextText()
                    else if (inner == XmlPullParser.START_TAG && p.name == "desc") desc = p.nextText()
                    inner = p.next()
                }
                if (channel in ids && start != null && end != null) out += HeroEpgProgramme(title, desc, start, end)
            }
            event = p.next()
        }
    } finally { c.disconnect() }
    return out.sortedBy { it.start }
}

private fun parseHeroEpgTime(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        val m = Regex("^(\\d{8})(\\d{4})(\\d{2})?\\s*(Z|[+-]\\d{4})?.*").find(value.trim()) ?: return null
        val raw = m.groupValues[1] + m.groupValues[2] + m.groupValues[3].ifBlank { "00" }
        val local = java.time.LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val tz = m.groupValues.getOrNull(4).orEmpty()
        when {
            tz == "Z" -> local.toInstant(java.time.ZoneOffset.UTC)
            Regex("[+-]\\d{4}").matches(tz) -> local.toInstant(java.time.ZoneOffset.of(tz.substring(0,3)+":"+tz.substring(3)))
            else -> local.atZone(ZoneId.of("Europe/Rome")).toInstant()
        }
    }.getOrNull()
}

private fun normalizeHeroEpgName(value: String): String {
    var n = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
    n = n.replace(Regex("\\((BACKUP|HD|FHD|UHD|4K)\\)"), " ")
        .replace(Regex("\\b(BACKUP|FHD|UHD|4K|HD)\\b"), " ")
    return n.replace(Regex("[^A-Z0-9]+"), " ").trim()
}
