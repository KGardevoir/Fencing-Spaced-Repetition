package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders a markdown-formatted string as styled Compose UI.
 *
 * Supported syntax:
 *   # H1, ## H2, ### H3  — headings
 *   **bold**              — bold inline
 *   *italic*              — italic inline
 *   `code`                — monospace inline
 *   - item / * item       — bullet list items
 *   blank lines           — vertical spacing
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            when {
                line.startsWith("# ") -> Text(
                    text = parseInlineMarkdown(line.removePrefix("# ")),
                    style = MaterialTheme.typography.headlineSmall
                )
                line.startsWith("## ") -> Text(
                    text = parseInlineMarkdown(line.removePrefix("## ")),
                    style = MaterialTheme.typography.titleLarge
                )
                line.startsWith("### ") -> Text(
                    text = parseInlineMarkdown(line.removePrefix("### ")),
                    style = MaterialTheme.typography.titleMedium
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = parseInlineMarkdown(
                            if (line.startsWith("- ")) line.removePrefix("- ")
                            else line.removePrefix("* ")
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                line.isBlank() -> Spacer(modifier = Modifier.height(4.dp))
                else -> Text(
                    text = parseInlineMarkdown(line),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Parses inline markdown spans (**bold**, *italic*, `code`, <u>underline</u>) into an [AnnotatedString].
 */
fun parseInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // <u>underline</u>
            text.startsWith("<u>", i) -> {
                val end = text.indexOf("</u>", i + 3)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 4
                } else {
                    append(text[i])
                    i++
                }
            }
            // **bold** — must be checked before single *
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // *italic* — only when not the second char of a ** sequence
            text[i] == '*' && (i == 0 || text[i - 1] != '*') -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // `code`
            text[i] == '`' -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
