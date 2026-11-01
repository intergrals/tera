package com.envelope.budget.util

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** ISO date helpers. Stored dates are always `yyyy-MM-dd` strings. */
object Dates {

    fun parseOrNull(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return try {
            LocalDate.parse(iso)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun parseOrToday(iso: String?): LocalDate = parseOrNull(iso) ?: LocalDate.now()

    fun todayIso(): String = LocalDate.now().toString()

    fun toIso(date: LocalDate): String = date.toString()

    fun toIsoOrNull(date: LocalDate?): String? = date?.toString()

    /** Advances a date by one recurrence step, keeping month-end anchored (Jan 31 -> Feb 28). */
    fun advance(date: LocalDate, recurrence: com.envelope.budget.domain.Recurrence): LocalDate =
        when (recurrence) {
            com.envelope.budget.domain.Recurrence.WEEKLY -> date.plusWeeks(1)
            com.envelope.budget.domain.Recurrence.BIWEEKLY -> date.plusWeeks(2)
            com.envelope.budget.domain.Recurrence.MONTHLY -> date.plusMonths(1)
            com.envelope.budget.domain.Recurrence.QUARTERLY -> date.plusMonths(3)
            com.envelope.budget.domain.Recurrence.YEARLY -> date.plusYears(1)
        }
}
