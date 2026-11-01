package com.tera.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tera.app.util.MoneyFormat

/**
 * Amount entry keypad. Digits are collected as a raw string where the last two digits
 * are cents, so "1234" is $12.34 — the same convention as a cash register. This avoids
 * keyboard quirks entirely (no IME, no decimal-separator locale bugs mid-entry).
 */
@Composable
fun AmountKeypad(
    digits: String,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { digit ->
                    KeypadKey(label = digit.toString(), onClick = { onDigit(digit) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeypadKey(
                label = "00",
                onClick = { onDigit('0'); onDigit('0') },
                modifier = Modifier.weight(1f),
            )
            KeypadKey(label = "0", onClick = { onDigit('0') }, modifier = Modifier.weight(1f))
            KeypadKey(
                label = null,
                onClick = onBackspace,
                modifier = Modifier.weight(1f),
                contentDescription = "Delete last digit",
            )
        }
    }
}

@Composable
private fun KeypadKey(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .size(width = 0.dp, height = 56.dp)
            .fillMaxWidth()
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Shows the keypad buffer as a formatted amount with a blinking-free caret style. */
@Composable
fun KeypadDisplay(digits: String, currency: String, modifier: Modifier = Modifier) {
    val cents = MoneyFormat.fromKeypadDigits(digits)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = MoneyFormat.format(cents, currency),
            style = com.tera.app.ui.theme.MoneyTextStyleLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
