package com.minitimer.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
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

/**
 * Reloj del player. Por defecto ([padded] = false) omite el "0:" delante cuando
 * falta menos de un minuto (30 s -> "30", 5 s -> "5"). Con [padded] = true usa
 * el formato completo con ceros a la izquierda (30 s -> "00:30"). 1:30 y 1:05:00
 * se mantienen en ambos casos.
 */
fun formatPlayerClock(ms: Long, padded: Boolean = false): String {
    val total = ceil(ms.coerceAtLeast(0) / 1000.0).toLong()
    val h = (total / 3600).toInt()
    val m = ((total % 3600) / 60).toInt()
    val s = (total % 60).toInt()
    return when {
        h > 0 -> "$h:${pad2(m)}:${pad2(s)}"
        padded -> "${pad2(m)}:${pad2(s)}"
        m > 0 -> "$m:${pad2(s)}"
        else -> "$s"
    }
}

/**
 * Duración compacta para el Now Bar: "10 m", "1 h 30 m", "1 m 30 s", "45 s".
 * Solo incluye las unidades distintas de cero.
 */
fun formatDurationShort(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = (total / 3600).toInt()
    val m = ((total % 3600) / 60).toInt()
    val s = (total % 60).toInt()
    val parts = buildList {
        if (h > 0) add("$h h")
        if (m > 0) add("$m m")
        if (s > 0) add("$s s")
    }
    return if (parts.isEmpty()) "0 s" else parts.joinToString(" ")
}

/**
 * Última finalización con formato relativo:
 * - Hoy: solo la hora ("6:35:01 PM").
 * - Ayer: "Ayer 6:35:01 PM".
 * - Antes: "Hace X días 28 jun 2026 6:35:01 PM".
 */
fun formatLastFinished(
    epochMillis: Long,
    locale: Locale,
    yesterdayLabel: String,
    daysAgoTemplate: String,
): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val time = dt.toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale))
    val days = ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now(zone))
    return when {
        days <= 0L -> time
        days == 1L -> "$yesterdayLabel $time"
        else -> {
            val date = dt.toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
            "${String.format(locale, daysAgoTemplate, days)} $date $time"
        }
    }
}

/** Etiqueta corta del incremento "+tiempo": "+15s", "+1m", "+5m". */
fun incLabel(sec: Int): String = if (sec >= 60) "+${sec / 60}m" else "+${sec}s"

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
