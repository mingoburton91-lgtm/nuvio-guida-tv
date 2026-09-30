package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.MetaPreview
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class HomeEpgProgramme(val title: String, val start: Instant, val end: Instant)

private val HOME_IT_EPG_URLS = listOf(
    "https://iptv-org.github.io/epg/guides/it/guidatv.sky.it.epg.xml",
    "https://iptv-org.github.io/epg/guides/it/mediaset.it.epg.xml"
)

internal fun enrichTvVooPreviewFromEpg(item: MetaPreview): MetaPreview? {
    val wanted = normalizeHomeEpgName(item.name)
    val now = Instant.now()
    val programmes = HOME_IT_EPG_URLS.asSequence()
        .mapNotNull { runCatching { readHomeEpg(it, wanted) }.getOrNull() }
        .firstOrNull { list -> list.any { !it.start.isAfter(now) && it.end.isAfter(now) } }
        ?: return null
    val index = programmes.indexOfFirst { !it.start.isAfter(now) && it.end.isAfter(now) }
    if (index < 0) return null
    val current = programmes[index]
    val next = programmes.getOrNull(index + 1)
    val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN).withZone(ZoneId.of("Europe/Rome"))
    val description = buildString {
        append("● IN ONDA  ").append(fmt.format(current.start)).append("–").append(fmt.format(current.end))
            .append(" · ").append(current.title)
        if (next != null) {
            append("\nA seguire  ").append(fmt.format(next.start)).append("–").append(fmt.format(next.end))
                .append(" · ").append(next.title)
        }
    }
    return item.copy(description = description)
}

private fun readHomeEpg(address: String, wantedName: String): List<HomeEpgProgramme> {
    fun open(): HttpURLConnection = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 20000
    }
    val ids = HashSet<String>()
    var connection = open()
    try {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(connection.inputStream, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "channel") {
                val id = parser.getAttributeValue(null, "id").orEmpty()
                val names = ArrayList<String>()
                var inner = parser.next()
                while (!(inner == XmlPullParser.END_TAG && parser.name == "channel")) {
                    if (inner == XmlPullParser.START_TAG && parser.name == "display-name") names.add(parser.nextText())
                    inner = parser.next()
                }
                val normalizedId = normalizeHomeEpgName(id.removeSuffix(".it"))
                if (names.any { normalizeHomeEpgName(it) == wantedName } ||
                    normalizedId == wantedName || normalizeHomeEpgName(id) == wantedName) ids.add(id)
            }
            event = parser.next()
        }
    } finally { connection.disconnect() }
    if (ids.isEmpty()) return emptyList()

    val programmes = ArrayList<HomeEpgProgramme>()
    connection = open()
    try {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(connection.inputStream, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val id = parser.getAttributeValue(null, "channel").orEmpty()
                val start = parseHomeEpgTime(parser.getAttributeValue(null, "start"))
                val end = parseHomeEpgTime(parser.getAttributeValue(null, "stop"))
                var title = "Programma"
                var inner = parser.next()
                while (!(inner == XmlPullParser.END_TAG && parser.name == "programme")) {
                    if (inner == XmlPullParser.START_TAG && parser.name == "title") title = parser.nextText()
                    inner = parser.next()
                }
                if (id in ids && start != null && end != null) programmes.add(HomeEpgProgramme(title, start, end))
            }
            event = parser.next()
        }
    } finally { connection.disconnect() }
    return programmes.sortedBy { it.start }
}

private fun parseHomeEpgTime(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return try {
        val m = Regex("^(\\d{8})(\\d{4})(\\d{2})?\\s*(Z|[+-]\\d{4})?.*").find(value.trim()) ?: return null
        val raw = m.groupValues[1] + m.groupValues[2] + m.groupValues[3].ifBlank { "00" }
        val local = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val tz = m.groupValues.getOrNull(4).orEmpty()
        when {
            tz == "Z" -> local.toInstant(java.time.ZoneOffset.UTC)
            Regex("[+-]\\d{4}").matches(tz) -> local.toInstant(java.time.ZoneOffset.of(tz.substring(0,3)+":"+tz.substring(3)))
            else -> local.atZone(ZoneId.of("Europe/Rome")).toInstant()
        }
    } catch (_: Exception) { null }
}

private fun normalizeHomeEpgName(value: String): String {
    var n = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
    n = n.replace(Regex("\\((BACKUP|HD|FHD|UHD|4K)\\)"), " ").replace(Regex("\\b(BACKUP|FHD|UHD|4K|HD)\\b"), " ")
    return n.replace(Regex("[^A-Z0-9]+"), " ").trim()
}
