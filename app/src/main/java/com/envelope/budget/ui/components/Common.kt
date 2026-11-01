package com.envelope.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.envelope.budget.domain.FlagColor
import com.envelope.budget.ui.theme.LocalEnvelopeColors
import com.envelope.budget.ui.theme.MoneyTextStyle
import com.envelope.budget.util.MoneyFormat
import androidx.compose.ui.text.TextStyle

/** Currency amount with a colour rule supplied by the caller (state, sign, target). */
@Composable
fun MoneyText(
    cents: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyTextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showPlus: Boolean = false,
    contentDescription: String? = null,
) {
    val text = if (showPlus) MoneyFormat.formatSigned(cents, currency) else MoneyFormat.format(cents, currency)
    val spoken = contentDescription ?: describe(cents, currency)
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = modifier.clearAndSetSemantics { this.contentDescription = spoken },
    )
}

private fun describe(cents: Long, currency: String): String {
    val amount = MoneyFormat.format(if (cents < 0) -cents else cents, currency, true)
    return when {
        cents > 0 -> "plus $amount"
        cents < 0 -> "minus $amount"
        else -> "zero"
    }
}

/** Red when negative, otherwise a calm neutral. */
@Composable
fun availableColor(cents: Long): Color = when {
    cents < 0 -> LocalEnvelopeColors.current.negative
    cents == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun SemanticsDot(color: Color, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { contentDescription = description },
    )
}

@Composable
fun FlagDot(flag: FlagColor, modifier: Modifier = Modifier) {
    if (flag == FlagColor.NONE) return
    val color = when (flag) {
        FlagColor.RED -> Color(0xFFE53935)
        FlagColor.ORANGE -> Color(0xFFFB8C00)
        FlagColor.YELLOW -> Color(0xFFFDD835)
        FlagColor.GREEN -> Color(0xFF43A047)
        FlagColor.BLUE -> Color(0xFF1E88E5)
        FlagColor.PURPLE -> Color(0xFF8E24AA)
        FlagColor.NONE -> Color.Transparent
    }
    SemanticsDot(color, "flagged ${flag.name.lowercase()}", modifier)
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        trailing?.invoke()
    }
}

/** Column header used by the budget grid and the register. */
@Composable
fun ColumnLabels(vararg labels: Pair<String, Float>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        labels.forEach { (label, weight) ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (weight == labels.first().second) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(weight),
            )
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
