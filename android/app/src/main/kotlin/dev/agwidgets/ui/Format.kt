package dev.agwidgets.ui

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/** Parse an ISO timestamp produced by the collector (offset or UTC). */
internal fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (_: Exception) {
        try {
            Instant.parse(iso)
        } catch (_: Exception) {
            null
        }
    }
}

internal fun statusAgeMinutes(iso: String?, nowMs: Long): Long? {
    val instant = parseInstant(iso) ?: return null
    return ((nowMs - instant.toEpochMilli()) / 60_000).coerceAtLeast(0)
}

internal fun updatedLabel(iso: String?, nowMs: Long): String {
    val age = statusAgeMinutes(iso, nowMs) ?: return "—"
    return when {
        age < 1 -> "только что"
        age < 60 -> "$age мин назад"
        age < 24 * 60 -> "${age / 60} ч назад"
        else -> "${age / (24 * 60)} д назад"
    }
}

internal fun formatCountdownShort(iso: String?, nowMs: Long): String? {
    val target = parseInstant(iso) ?: return null
    val totalSeconds = Duration.between(Instant.ofEpochMilli(nowMs), target).seconds
    if (totalSeconds <= 0) return "сброс сейчас"
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        days > 0 -> "${days}д ${hours}ч"
        hours > 0 -> "${hours}ч ${minutes}м"
        else -> "${minutes}м"
    }
}

internal fun formatDateShort(iso: String): String {
    val instant = parseInstant(iso) ?: return "—"
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return "%02d.%02d".format(date.dayOfMonth, date.monthValue)
}

internal fun formatK(value: Int): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}

internal fun formatUnits(value: Long): String = when {
    value >= 1_000_000_000L -> "${value / 1_000_000_000L}B"
    value >= 1_000_000L -> "${value / 1_000_000L}M"
    value >= 1_000L -> "${value / 1_000L}K"
    else -> value.toString()
}

/** "claude-opus-5-5-high" -> "Claude Opus 5.5". */
internal fun prettyModel(raw: String): String {
    val efforts = setOf("low", "medium", "high", "xhigh", "max", "minimal")
    val parts = raw.split('-').filter { it.isNotBlank() }
    val trimmed = if (parts.size > 1 && parts.last().lowercase() in efforts) parts.dropLast(1) else parts
    val base = trimmed.joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
    return Regex("""\b(\d) (\d)\b""").replace(base) { match -> "${match.groupValues[1]}.${match.groupValues[2]}" }
}
