package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

private enum class DescriptionTab { Edit, Preview }

/**
 * A Description input field with:
 *   - Edit / Preview tab toggle
 *   - Markdown formatting actions (Bold, Italic, Underline, Code, Heading, Bullet)
 *     available in the long-press text-selection popup via a custom [TextToolbar]
 *   - Preview renders the markdown via [MarkdownText]
 */
@Composable
fun MarkdownDescriptionField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Description",
    minLines: Int = 3,
    maxLines: Int = 8
) {
    var activeTab by remember { mutableStateOf(DescriptionTab.Edit) }

    val view = LocalView.current
    val toolbar = remember(view) { MarkdownTextToolbar(view) }

    // Reassign on every recomposition so lambdas always capture the latest value.
    toolbar.onBold      = { onValueChange(applyInlineFormat(value, "**",  "**",   "bold"))      }
    toolbar.onItalic    = { onValueChange(applyInlineFormat(value, "*",   "*",    "italic"))    }
    toolbar.onUnderline = { onValueChange(applyInlineFormat(value, "<u>", "</u>", "underline")) }
    toolbar.onCode      = { onValueChange(applyInlineFormat(value, "`",   "`",    "code"))      }
    toolbar.onHeader    = { onValueChange(applyLinePrefix(value, "# "))  }
    toolbar.onBullet    = { onValueChange(applyLinePrefix(value, "- "))  }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            TabRow(selectedTabIndex = activeTab.ordinal) {
                Tab(
                    selected = activeTab == DescriptionTab.Edit,
                    onClick = { activeTab = DescriptionTab.Edit },
                    text = { Text("Edit") },
                    icon = {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                Tab(
                    selected = activeTab == DescriptionTab.Preview,
                    onClick = { activeTab = DescriptionTab.Preview },
                    text = { Text("Preview") },
                    icon = {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            when (activeTab) {
                DescriptionTab.Edit -> {
                    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            label = { Text(label) },
                            placeholder = { Text("Long-press selected text to format: bold, italic, underline, code, heading, bullet...") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            },
                            minLines = minLines,
                            maxLines = maxLines,
                            shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                        )
                    }
                }

                DescriptionTab.Preview -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 120.dp)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp
                    ) {
                        if (value.text.isBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nothing to preview yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            MarkdownText(
                                text = value.text,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps the current selection (or inserts a placeholder) with [prefix] and [suffix].
 * After insertion the placeholder text is selected so the user can type over it.
 */
fun applyInlineFormat(
    fieldValue: TextFieldValue,
    prefix: String,
    suffix: String,
    placeholder: String
): TextFieldValue {
    val sel = fieldValue.selection
    val text = fieldValue.text
    return if (sel.collapsed) {
        val insert = "$prefix$placeholder$suffix"
        val newText = text.substring(0, sel.start) + insert + text.substring(sel.start)
        TextFieldValue(
            text = newText,
            selection = TextRange(sel.start + prefix.length, sel.start + prefix.length + placeholder.length)
        )
    } else {
        val selected = text.substring(sel.min, sel.max)
        val insert = "$prefix$selected$suffix"
        val newText = text.substring(0, sel.min) + insert + text.substring(sel.max)
        TextFieldValue(
            text = newText,
            selection = TextRange(sel.min + prefix.length, sel.min + prefix.length + selected.length)
        )
    }
}

/**
 * Inserts [linePrefix] at the beginning of the line containing the cursor.
 */
fun applyLinePrefix(
    fieldValue: TextFieldValue,
    linePrefix: String
): TextFieldValue {
    val text = fieldValue.text
    val cursor = fieldValue.selection.min
    val lineStart = (text.lastIndexOf('\n', cursor - 1) + 1).coerceAtLeast(0)
    val newText = text.substring(0, lineStart) + linePrefix + text.substring(lineStart)
    val shift = linePrefix.length
    return TextFieldValue(
        text = newText,
        selection = TextRange(
            fieldValue.selection.start + shift,
            fieldValue.selection.end + shift
        )
    )
}
