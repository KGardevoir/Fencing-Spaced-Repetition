package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

private enum class DescriptionTab { Edit, Preview }

/**
 * A Description input field with:
 *   - Edit / Preview tab toggle
 *   - Markdown formatting toolbar that slides in above the field whenever it
 *     is focused (i.e. the soft keyboard is open), then disappears on blur
 *   - Preview renders the markdown via [MarkdownText]
 */
@Composable
fun MarkdownDescriptionField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Description",
    minLines: Int = 3,
    maxLines: Int = 8,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var activeTab by remember { mutableStateOf(DescriptionTab.Edit) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Edit / Preview tabs
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
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        label = { Text(label) },
                        placeholder = { Text("Supports **bold**, *italic*, <u>underline</u>, # headings, - bullets...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { onFocusChanged(it.isFocused) },
                        leadingIcon = {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                        },
                        minLines = minLines,
                        maxLines = maxLines,
                        shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                    )
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

@Composable
fun MarkdownToolbar(
    onBold:      () -> Unit,
    onItalic:    () -> Unit,
    onUnderline: () -> Unit,
    onCode:      () -> Unit,
    onHeader:    () -> Unit,
    onBullet:    () -> Unit,
    modifier:    Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarIconButton(Icons.Default.FormatBold,          "Bold (**text**)",         onBold)
        ToolbarIconButton(Icons.Default.FormatItalic,        "Italic (*text*)",          onItalic)
        ToolbarIconButton(Icons.Default.FormatUnderlined,    "Underline (<u>text</u>)",  onUnderline)
        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
        ToolbarIconButton(Icons.Default.Title,               "Heading (# text)",         onHeader)
        ToolbarIconButton(Icons.Default.FormatListBulleted,  "Bullet list (- text)",     onBullet)
        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
        TextButton(
            onClick = onCode,
            modifier = Modifier.size(36.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "</>",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/**
 * Wraps the current selection (or inserts a placeholder) with [prefix] and [suffix].
 */
fun applyInlineFormat(
    fieldValue: TextFieldValue,
    prefix:      String,
    suffix:      String,
    placeholder: String
): TextFieldValue {
    val sel  = fieldValue.selection
    val text = fieldValue.text
    return if (sel.collapsed) {
        val insert  = "$prefix$placeholder$suffix"
        val newText = text.substring(0, sel.start) + insert + text.substring(sel.start)
        TextFieldValue(
            text      = newText,
            selection = TextRange(sel.start + prefix.length, sel.start + prefix.length + placeholder.length)
        )
    } else {
        val selected = text.substring(sel.min, sel.max)
        val insert   = "$prefix$selected$suffix"
        val newText  = text.substring(0, sel.min) + insert + text.substring(sel.max)
        TextFieldValue(
            text      = newText,
            selection = TextRange(sel.min + prefix.length, sel.min + prefix.length + selected.length)
        )
    }
}

/**
 * Inserts [linePrefix] at the beginning of the line containing the cursor.
 */
fun applyLinePrefix(
    fieldValue: TextFieldValue,
    linePrefix:  String
): TextFieldValue {
    val text      = fieldValue.text
    val cursor    = fieldValue.selection.min
    val lineStart = (text.lastIndexOf('\n', cursor - 1) + 1).coerceAtLeast(0)
    val newText   = text.substring(0, lineStart) + linePrefix + text.substring(lineStart)
    val shift     = linePrefix.length
    return TextFieldValue(
        text      = newText,
        selection = TextRange(fieldValue.selection.start + shift, fieldValue.selection.end + shift)
    )
}
