package com.tera.app.domain

import com.tera.app.util.MoneyFormat
import java.time.LocalDate

/**
 * Validation for the envelope editor. Kept pure and Android-free so the rules are
 * unit-tested directly instead of only through the UI.
 */
object EnvelopeRules {

    const val MAX_NAME_LENGTH = 60

    sealed interface Result {
        data class Valid(
            val name: String,
            val note: String,
            val amountCents: Long?,
            val date: LocalDate?,
        ) : Result

        data class Invalid(val message: String) : Result
    }

    fun validate(
        name: String,
        note: String,
        type: TargetType,
        amountText: String,
        date: LocalDate?,
    ): Result {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return Result.Invalid("Give the envelope a name")
        if (cleanName.length > MAX_NAME_LENGTH) {
            return Result.Invalid("Keep the name under $MAX_NAME_LENGTH characters")
        }

        if (type == TargetType.NONE) {
            return Result.Valid(cleanName, note.trim(), null, null)
        }

        val amount = MoneyFormat.parse(amountText)
        if (amount == null) return Result.Invalid("Enter a target amount")
        if (amount <= 0L) return Result.Invalid("The target must be more than zero")
        if (amount > MoneyFormat.MAX_ABS_CENTS) return Result.Invalid("That target is too large")

        val targetDate = if (type == TargetType.NEEDED_FOR_SPENDING) date else null
        return Result.Valid(cleanName, note.trim(), amount, targetDate)
    }
}
