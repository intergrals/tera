package com.tera.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Budget months are `yyyy-MM` strings. Using a string key (rather than a date) keeps
 * the Room primary key stable and avoids timezone drift entirely: a budget month is a
 * calendar month, never an instant.
 */
object MonthKey {

    private val labelFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val shortLabelFormatter = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
    private val dayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val dayYearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    fun of(year: Int, month: Int): String =
        year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0')

    fun of(date: LocalDate): String = of(date.year, date.monthValue)

    fun of(month: YearMonth): String = of(month.year, month.monthValue)

    fun current(): String = of(LocalDate.now())

    fun parse(key: String): YearMonth = YearMonth.parse(key)

    fun next(key: String): String = of(parse(key).plusMonths(1))

    fun previous(key: String): String = of(parse(key).minusMonths(1))

    fun label(key: String): String = parse(key).format(labelFormatter)

    fun shortLabel(key: String): String = parse(key).format(shortLabelFormatter)

    /** Inclusive list of month keys from [start] to [end]; empty if start > end. */
    fun range(start: String, end: String): List<String> {
        val first = parse(start)
        val last = parse(end)
        if (first.isAfter(last)) return emptyList()
        val months = ArrayList<String>()
        var cursor = first
        while (!cursor.isAfter(last)) {
            months.add(of(cursor))
            cursor = cursor.plusMonths(1)
        }
        return months
    }

    /** The [count] months ending at [end], oldest first. */
    fun trailingMonths(end: String, count: Int): List<String> {
        if (count <= 0) return emptyList()
        val last = parse(end)
        return (count - 1 downTo 0).map { of(last.minusMonths(it.toLong())) }
    }

    fun isFuture(key: String, today: LocalDate = LocalDate.now()): Boolean =
        parse(key).isAfter(YearMonth.from(today))

    fun formatDay(date: LocalDate): String = date.format(dayFormatter)

    fun formatDayYear(date: LocalDate): String = date.format(dayYearFormatter)
}
