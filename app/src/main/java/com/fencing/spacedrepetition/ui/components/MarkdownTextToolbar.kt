package com.fencing.spacedrepetition.ui.components

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

private const val ID_CUT        = android.R.id.cut
private const val ID_COPY       = android.R.id.copy
private const val ID_PASTE      = android.R.id.paste
private const val ID_SELECT_ALL = android.R.id.selectAll
private const val ID_BOLD       = 1001
private const val ID_ITALIC     = 1002
private const val ID_UNDERLINE  = 1003
private const val ID_CODE       = 1004
private const val ID_HEADER     = 1005
private const val ID_BULLET     = 1006

/**
 * A custom [TextToolbar] that adds markdown formatting actions to the floating
 * text-selection popup that appears on long-press, alongside the standard
 * Cut / Copy / Paste / Select All actions.
 *
 * Assign [onBold], [onItalic] etc. on every recomposition so the lambdas always
 * capture the latest [TextFieldValue] — they are invoked at tap time, not when
 * the menu is created.
 */
class MarkdownTextToolbar(private val view: View) : TextToolbar {

    var onBold:      (() -> Unit)? = null
    var onItalic:    (() -> Unit)? = null
    var onUnderline: (() -> Unit)? = null
    var onCode:      (() -> Unit)? = null
    var onHeader:    (() -> Unit)? = null
    var onBullet:    (() -> Unit)? = null

    private var actionMode: ActionMode? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested:      (() -> Unit)?,
        onPasteRequested:     (() -> Unit)?,
        onCutRequested:       (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        val contentRect = android.graphics.Rect(
            rect.left.toInt(), rect.top.toInt(),
            rect.right.toInt(), rect.bottom.toInt()
        )

        val callback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                var order = 0
                onCutRequested?.let {
                    menu.add(0, ID_CUT,        order++, "Cut")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                onCopyRequested?.let {
                    menu.add(0, ID_COPY,       order++, "Copy")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                onPasteRequested?.let {
                    menu.add(0, ID_PASTE,      order++, "Paste")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                onSelectAllRequested?.let {
                    menu.add(0, ID_SELECT_ALL, order++, "Select all")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                onBold?.let {
                    menu.add(0, ID_BOLD,      order++, "Bold")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                onItalic?.let {
                    menu.add(0, ID_ITALIC,    order++, "Italic")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                onUnderline?.let {
                    menu.add(0, ID_UNDERLINE, order++, "Underline")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                onCode?.let {
                    menu.add(0, ID_CODE,      order++, "Code")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                onHeader?.let {
                    menu.add(0, ID_HEADER,    order++, "Heading")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                onBullet?.let {
                    menu.add(0, ID_BULLET,    order++, "Bullet")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                // Read formatting lambdas from the class fields at tap time so they
                // always operate on the latest TextFieldValue.
                val handled = when (item.itemId) {
                    ID_CUT        -> { onCutRequested?.invoke();       true }
                    ID_COPY       -> { onCopyRequested?.invoke();      true }
                    ID_PASTE      -> { onPasteRequested?.invoke();     true }
                    ID_SELECT_ALL -> { onSelectAllRequested?.invoke(); true }
                    ID_BOLD       -> { onBold?.invoke();               true }
                    ID_ITALIC     -> { onItalic?.invoke();             true }
                    ID_UNDERLINE  -> { onUnderline?.invoke();          true }
                    ID_CODE       -> { onCode?.invoke();               true }
                    ID_HEADER     -> { onHeader?.invoke();             true }
                    ID_BULLET     -> { onBullet?.invoke();             true }
                    else          -> false
                }
                if (handled) mode.finish()
                return handled
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                status = TextToolbarStatus.Hidden
            }

            override fun onGetContentRect(
                mode: ActionMode,
                view: View,
                outRect: android.graphics.Rect
            ) {
                outRect.set(contentRect)
            }
        }

        actionMode?.finish()
        actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        actionMode?.finish()
        actionMode = null
        status = TextToolbarStatus.Hidden
    }
}
