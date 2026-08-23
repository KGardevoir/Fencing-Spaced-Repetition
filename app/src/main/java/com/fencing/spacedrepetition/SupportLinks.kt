// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition

/**
 * External links offered in the "Support Development" settings section.
 *
 * These are plain outbound URLs opened in the user's browser rather than an
 * in-app purchase flow. Google Play Billing is a proprietary dependency and
 * would disqualify the app from F-Droid, which only builds software whose
 * dependencies are all free software.
 *
 * Add or remove entries here; the settings UI renders whatever this list holds
 * and hides the section entirely when it is empty.
 */
object SupportLinks {

    data class Link(
        val label: String,
        val description: String,
        val url: String
    )

    val links: List<Link> = listOf(
        Link(
            label = "GitHub Sponsors",
            description = "One-off or recurring, handled by GitHub",
            url = "https://github.com/sponsors/KGardevoir"
        )
    )
}
