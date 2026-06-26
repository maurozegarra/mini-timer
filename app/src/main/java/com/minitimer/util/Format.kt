package com.minitimer.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil

fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** Formatea milisegundos restantes como m:ss o h:mm:ss. */
fun formatRemaining(ms: Long): String {
    val total = ceil(ms.coerceAtLeast(0) / 1000.0).toLong()
    val h = (total / 3600).toInt()
    val m = ((total % 3600) / 60).toInt()
    val s = (total % 60).toInt()
    return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "$m:${pad2(s)}"
}

/** Hora de finalización con segundos, respetando el locale (12/24h). */
fun formatClock(epochMillis: Long, locale: Locale): String {
    val time = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    return time.format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)
    )
}

/**
 * Convierte una entrada de preset a segundos.
 * - "90"        -> 90 minutos? No: un solo número = minutos (90 min). Usa "mm:ss" para minutos:segundos.
 * - "5"         -> 5 minutos
 * - "1:30"      -> 90 segundos
 * - "1:00:00"   -> 3600 segundos
 */
fun parsePresetInput(input: String): Int {
    val clean = input.trim()
    if (clean.isEmpty()) return 0
    val parts = clean.split(":").map { it.trim().toIntOrNull() ?: return 0 }
    val sec = when (parts.size) {
        1 -> parts[0] * 60
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
    return if (sec > 0) sec else 0
}

fun dedupeSorted(list: List<Int>): List<Int> = list.toSortedSet().toList()
