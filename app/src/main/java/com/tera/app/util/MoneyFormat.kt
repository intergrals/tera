package com.tera.app.util

/**
 * Currency formatting and parsing for integer-cent money.
 *
 * Every amount in TERA is a [Long] count of cents; no floating point is ever
 * involved in money math. Formatting is deterministic (not locale-grouped) so the
 * same number renders identically on every device, with the user's chosen symbol.
 */
object MoneyFormat {

    /** ~$9,999,999.99 — larger amounts are rejected as almost certainly a typo. */
    const val MAX_ABS_CENTS: Long = 999_999_999_00L

    fun format(cents: Long, symbol: String = "$"): String = format(cents, symbol, true)

    fun format(cents: Long, symbol: String, showSymbol: Boolean): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val whole = abs / 100
        val frac = (abs % 100).toInt()
        return buildString {
            if (negative) append('-')
            if (showSymbol) append(symbol)
            append(groupDigits(whole))
            append('.')
            if (frac < 10) append('0')
            append(frac)
        }
    }

    /** Amount without the decimal part, for chart labels and tight rows. */
    fun formatWhole(cents: Long, symbol: String = "$"): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        return buildString {
            if (negative) append('-')
            append(symbol)
            append(groupDigits(abs / 100))
        }
    }

    /** Always shows a leading sign; used in Activity columns and reports. */
    fun formatSigned(cents: Long, symbol: String = "$"): String {
        val body = format(cents, symbol)
        return if (cents > 0) "+$body" else body
    }

    fun groupDigits(value: Long): String {
        val digits = value.toString()
        return buildString {
            digits.forEachIndexed { index, char ->
                if (index > 0 && (digits.length - index) % 3 == 0) append(',')
                append(char)
            }
        }
    }

    /**
     * Parses free-form user input into cents, tolerating a comma or period decimal
     * separator, currency symbols, and pasted separators. Returns null when the text
     * cannot be read as an amount.
     */
    fun parse(input: String): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val negative = trimmed.startsWith("-")
        val cleaned = trimmed.filter { it.isDigit() || it == '.' || it == ',' }
        if (cleaned.isEmpty()) return null

        val lastSeparator = cleaned.indexOfLast { it == '.' || it == ',' }
        var wholeText = cleaned
        var fractionText = ""
        if (lastSeparator >= 0) {
            val after = cleaned.substring(lastSeparator + 1)
            // 1-2 trailing digits after the final separator means it is a decimal point;
            // three or more digits means grouping ("1,234").
            if (after.length in 1..2 && after.all { it.isDigit() }) {
                wholeText = cleaned.substring(0, lastSeparator)
                fractionText = after
            }
        }

        val whole = wholeText.filter { it.isDigit() }
        if (whole.isEmpty() && fractionText.isEmpty()) return null
        val wholeValue = if (whole.isEmpty()) 0L else whole.toLongOrNull() ?: return null
        if (wholeValue > MAX_ABS_CENTS / 100) return null

        var fraction = fractionText.ifEmpty { "0" }
        if (fraction.length == 1) fraction += "0"
        if (fraction.length > 2) fraction = fraction.take(2)

        val total = wholeValue * 100 + fraction.toLong()
        return if (negative) -total else total
    }

    /** Digits typed on the amount keypad -> cents, where the last two digits are cents. */
    fun fromKeypadDigits(digits: String): Long {
        val onlyDigits = digits.filter { it.isDigit() }.trimStart('0').ifEmpty { "0" }
        val value = onlyDigits.toLongOrNull() ?: 0L
        return value.coerceAtMost(MAX_ABS_CENTS)
    }

    /** Cents -> keypad digit buffer (inverse of [fromKeypadDigits]), e.g. 1234 -> "1234". */
    fun toKeypadDigits(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        return abs.toString()
    }
}
