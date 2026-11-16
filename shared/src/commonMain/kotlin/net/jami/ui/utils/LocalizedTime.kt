/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.utils

import androidx.compose.runtime.Composable
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Date and time labels built from the string catalogue, so they follow the app language.
// kotlinx-datetime has no locale-aware formatting, hence the month names and per-language
// patterns as resources rather than a platform formatter.

private val MONTHS: List<StringResource> = listOf(
    Res.string.date_month_1, Res.string.date_month_2, Res.string.date_month_3,
    Res.string.date_month_4, Res.string.date_month_5, Res.string.date_month_6,
    Res.string.date_month_7, Res.string.date_month_8, Res.string.date_month_9,
    Res.string.date_month_10, Res.string.date_month_11, Res.string.date_month_12,
)

/** "Today", "Yesterday", or the full date ("November 14, 2026" / "14. November 2026"). */
@Composable
fun dateSeparatorLabel(timestampMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(timestampMillis).toLocalDateTime(tz).date
    val today = Clock.System.now().toLocalDateTime(tz).date
    return when (date) {
        today -> stringResource(Res.string.date_today)
        today.minus(DatePeriod(days = 1)) -> stringResource(Res.string.date_yesterday)
        else -> stringResource(
            Res.string.date_full_format,
            stringResource(MONTHS[date.monthNumber - 1]),
            date.dayOfMonth,
            date.year,
        )
    }
}

/** Compact age for the conversation list: "Now", "5m", "3h", "2d", "1w" (in English). */
@Composable
fun compactAgeLabel(timestampMillis: Long, nowMillis: Long = net.jami.utils.currentTimeMillis()): String {
    if (timestampMillis <= 0L) return ""
    val diffSeconds = (nowMillis - timestampMillis) / 1000
    return when {
        diffSeconds < 60 -> stringResource(Res.string.time_now_short)
        diffSeconds < 3600 -> stringResource(Res.string.time_minutes_short, (diffSeconds / 60).toInt())
        diffSeconds < 86400 -> stringResource(Res.string.time_hours_short, (diffSeconds / 3600).toInt())
        diffSeconds < 604800 -> stringResource(Res.string.time_days_short, (diffSeconds / 86400).toInt())
        else -> stringResource(Res.string.time_weeks_short, (diffSeconds / 604800).toInt())
    }
}
