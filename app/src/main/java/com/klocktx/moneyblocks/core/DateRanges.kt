package com.klocktx.moneyblocks.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TimeRange(val start: Long, val endExclusive: Long)

val zone: ZoneId get() = ZoneId.systemDefault()

fun LocalDate.startMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun LocalDate.endExclusiveMillis(): Long = plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun today(): LocalDate = LocalDate.now(zone)

fun TimeRange.contains(millis: Long): Boolean = millis in start until endExclusive

fun monthRange(year: Int, monthValue: Int): TimeRange {
    val first = LocalDate.of(year, monthValue, 1)
    return TimeRange(first.startMillis(), first.plusMonths(1).startMillis())
}

fun dayRange(date: LocalDate): TimeRange = TimeRange(date.startMillis(), date.endExclusiveMillis())

/** 近 n 天（含今天）的连续区间，用于趋势图。 */
fun trailingDayRange(days: Int): TimeRange {
    val startDay = today().minusDays((days - 1).coerceAtLeast(0).toLong())
    return TimeRange(startDay.startMillis(), today().endExclusiveMillis())
}

fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
private val weekdayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", java.util.Locale.CHINESE)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(millis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))

fun formatMonth(date: LocalDate): String = monthFormatter.format(date)

fun formatDay(date: LocalDate): String = dayFormatter.format(date)

fun formatWeekday(date: LocalDate): String = weekdayFormatter.format(date)

/** 「今天 / 昨天 / 3月5日」这类相对标签，列表按日分组时使用。 */
fun relativeDayLabel(date: LocalDate): String = when (date) {
    today() -> "今天"
    today().minusDays(1) -> "昨天"
    else -> formatDay(date)
}
