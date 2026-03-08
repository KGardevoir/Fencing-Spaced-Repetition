package com.fencing.spacedrepetition.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Holds the state that connects a focused [MarkdownDescriptionField] to a
 * screen-level [MarkdownKeyboardToolbar]. Call [onFieldFocused] /
 * [onFieldBlurred] from each editor field's focus callback, and the toolbar
 * will automatically format the correct field.
 */
@Stable
class MarkdownToolbarState {
    /** The current [TextFieldValue] of the focused editor (null = nothing focused). */
    var activeFieldValue: TextFieldValue? by mutableStateOf(null)
        private set

    /** Callback supplied by the focused editor to accept formatting changes. */
    private var activeOnValueChange: ((TextFieldValue) -> Unit)? = null

    /** Whether any markdown editor field is currently focused. */
    val isFocused: Boolean get() = activeFieldValue != null

    /**
     * Called by a markdown editor field when it gains focus.
     * [currentValue] is the field's current text; [onValueChange] is the
     * callback used to push formatted text back into the field.
     */
    fun onFieldFocused(currentValue: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
        activeFieldValue = currentValue
        activeOnValueChange = onValueChange
    }

    /** Called by a markdown editor field when it loses focus. */
    fun onFieldBlurred() {
        activeFieldValue = null
        activeOnValueChange = null
    }

    /** Called by the field whenever its value changes, to keep the toolbar in sync. */
    fun onFieldValueChanged(newValue: TextFieldValue) {
        activeFieldValue = newValue
    }

    /** Apply an inline formatting operation (bold, italic, etc.). */
    fun applyInline(prefix: String, suffix: String, placeholder: String) {
        val value = activeFieldValue ?: return
        val updated = applyInlineFormat(value, prefix, suffix, placeholder)
        activeFieldValue = updated
        activeOnValueChange?.invoke(updated)
    }

    /** Apply a line-prefix formatting operation (heading, bullet). */
    fun applyPrefix(linePrefix: String) {
        val value = activeFieldValue ?: return
        val updated = applyLinePrefix(value, linePrefix)
        activeFieldValue = updated
        activeOnValueChange?.invoke(updated)
    }
}

@Composable
fun rememberMarkdownToolbarState(): MarkdownToolbarState {
    return remember { MarkdownToolbarState() }
}

/**
 * A keyboard-pinned markdown formatting toolbar. Place this at the **bottom**
 * of your screen's outer Column (which should have `.imePadding()`).
 *
 * It automatically shows/hides based on whether a connected
 * [MarkdownDescriptionField] is focused and a keyboard is visible.
 *
 * Usage:
 * ```
 * val toolbarState = rememberMarkdownToolbarState()
 *
 * Column(Modifier.fillMaxSize().imePadding()) {
 *     // scrollable content with MarkdownDescriptionField(s) …
 *     //   pass toolbarState to connect them
 *     MarkdownKeyboardToolbar(toolbarState)
 * }
 * ```
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkdownKeyboardToolbar(
    state: MarkdownToolbarState,
    modifier: Modifier = Modifier
) {
    val isImeVisible = WindowInsets.isImeVisible
    val configuration = LocalConfiguration.current
    val hasPhysicalKeyboard =
        configuration.keyboard == Configuration.KEYBOARD_QWERTY ||
        configuration.keyboard == Configuration.KEYBOARD_12KEY

    AnimatedVisibility(
        visible = state.isFocused && (isImeVisible || hasPhysicalKeyboard),
        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider()
            MarkdownToolbar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                onBold      = { state.applyInline("**",  "**",   "bold") },
                onItalic    = { state.applyInline("*",   "*",    "italic") },
                onUnderline = { state.applyInline("<u>", "</u>", "underline") },
                onCode      = { state.applyInline("`",   "`",    "code") },
                onHeader    = { state.applyPrefix("# ") },
                onBullet    = { state.applyPrefix("- ") }
            )
        }
    }
}
