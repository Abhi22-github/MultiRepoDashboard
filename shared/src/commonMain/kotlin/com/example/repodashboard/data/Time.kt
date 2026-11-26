@file:OptIn(ExperimentalTime::class)

package com.example.repodashboard.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

fun isoToMs(iso: String?): Long? =
    if (iso.isNullOrBlank()) null else try { Instant.parse(iso).toEpochMilliseconds() } catch (_: Exception) { null }

/** "2 h", "1 d", "3 w", "4 mo", "1 y" — the compact age used across the inbox. */
fun ageShort(iso: String?, now: Long = nowMs()): String {
    val t = isoToMs(iso) ?: return ""
    val mins = ((now - t) / 60_000).coerceAtLeast(0)
    return when {
        mins < 60 -> "${mins.coerceAtLeast(1)} m"
        mins < 60 * 24 -> "${mins / 60} h"
        mins < 60 * 24 * 7 -> "${mins / (60 * 24)} d"
        mins < 60 * 24 * 30 -> "${mins / (60 * 24 * 7)} w"
        mins < 60 * 24 * 365 -> "${mins / (60 * 24 * 30)} mo"
        else -> "${mins / (60 * 24 * 365)} y"
    }
}

/** "2026-11-20T…" -> "Nov 20". */
fun monthDay(iso: String?): String {
    if (iso == null || iso.length < 10) return ""
    val m = iso.substring(5, 7).toIntOrNull() ?: return ""
    val d = iso.substring(8, 10).toIntOrNull() ?: return ""
    return "${MONTHS.getOrElse(m - 1) { "" }} $d"
}

fun monthDayYear(iso: String?): String {
    val md = monthDay(iso)
    return if (md.isEmpty()) "" else "$md, ${iso!!.take(4)}"
}

fun syncedLabel(syncedAt: Long?, now: Long = nowMs()): String {
    if (syncedAt == null) return "not synced"
    val mins = (now - syncedAt) / 60_000
    return when {
        mins < 1 -> "synced just now"
        mins < 60 -> "synced $mins min ago"
        else -> "synced ${mins / 60} h ago"
    }
}

fun minutesUntil(epochSeconds: Long?, now: Long = nowMs()): Long? =
    epochSeconds?.let { ((it * 1000 - now) / 60_000).coerceAtLeast(1) }
