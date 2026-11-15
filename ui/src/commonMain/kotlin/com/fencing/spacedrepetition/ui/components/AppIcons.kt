// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own icons, for the two ideas Material has no picture of.
 *
 * Both are built from one card box -- a lid, a body a little narrower than
 * it, and a handle slot under the lid -- drawn as a filled lid over an
 * outlined body, which keeps the lid legible at 24dp where an outline of it
 * closes up. They are tinted like any other icon: the colour here is only a
 * placeholder that `Icon` replaces.
 */
object AppIcons {

    private const val STROKE = 1.5f

    /** Card groups: three card boxes stacked, for "a collection of decks". */
    val CardGroups: ImageVector by lazy {
        icon("CardGroups") {
            fill(
                "M1.60,12.80H10.40Q11.00,12.80 11.00,13.40V14.70Q11.00,15.30 10.40,15.30H1.60" +
                    "Q1.00,15.30 1.00,14.70V13.40Q1.00,12.80 1.60,12.80Z " +
                    "M13.60,12.80H22.40Q23.00,12.80 23.00,13.40V14.70Q23.00,15.30 22.40,15.30H13.60" +
                    "Q13.00,15.30 13.00,14.70V13.40Q13.00,12.80 13.60,12.80Z " +
                    "M7.60,2.00H16.40Q17.00,2.00 17.00,2.60V3.90Q17.00,4.50 16.40,4.50H7.60" +
                    "Q7.00,4.50 7.00,3.90V2.60Q7.00,2.00 7.60,2.00Z"
            )
            stroke(
                "M2.75,15.30V21.65H9.25V15.30 M4.60,15.30V16.83H7.40V15.30 " +
                    "M14.75,15.30V21.65H21.25V15.30 M16.60,15.30V16.83H19.40V15.30 " +
                    "M8.75,4.50V10.85H15.25V4.50 M10.60,4.50V6.03H13.40V4.50"
            )
        }
    }

    /** Learning groups: one card box with a brain beside it, for "shared learning progress". */
    val LearningGroup: ImageVector by lazy {
        icon("LearningGroup") {
            fill(
                "M1.60,10.00H12.90Q13.50,10.00 13.50,10.60V12.65Q13.50,13.25 12.90,13.25H1.60" +
                    "Q1.00,13.25 1.00,12.65V10.60Q1.00,10.00 1.60,10.00Z"
            )
            stroke("M3.00,13.25V21.75H11.50V13.25 M5.50,13.25V15.25H9.00V13.25")
            // The brain is drawn at a comfortable size and scaled up into the
            // top right corner; its stroke is thinned by the same factor so it
            // matches the box's.
            addGroup(
                name = "brain",
                pivotX = 18.2f,
                pivotY = 5.7f,
                scaleX = BRAIN_SCALE,
                scaleY = BRAIN_SCALE,
                translationX = -0.8f,
                translationY = 0.6f
            )
            stroke(
                "M18.2,2.6 C17.4,1.6 15.6,1.9 15.3,3.1 C14.0,3.2 13.3,4.5 13.9,5.5 " +
                    "C13.1,6.2 13.3,7.8 14.5,8.1 C14.8,9.4 16.8,9.8 17.5,8.8 C17.8,9.0 18.0,9.0 18.2,8.9 " +
                    "C18.4,9.0 18.6,9.0 18.9,8.8 C19.6,9.8 21.6,9.4 21.9,8.1 C23.1,7.8 23.3,6.2 22.5,5.5 " +
                    "C23.1,4.5 22.4,3.2 21.1,3.1 C20.8,1.9 19.0,1.6 18.2,2.6 Z " +
                    "M18.2,2.6 V8.9 " +
                    "M15.4,5.0 C16.4,5.0 16.6,5.9 16.4,6.6 " +
                    "M21.0,5.0 C20.0,5.0 19.8,5.9 20.0,6.6",
                width = STROKE / BRAIN_SCALE
            )
            clearGroup()
        }
    }

    private const val BRAIN_SCALE = 1.2f

    private inline fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    private fun ImageVector.Builder.fill(path: String) {
        addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
    }

    private fun ImageVector.Builder.stroke(path: String, width: Float = STROKE) {
        addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
}
