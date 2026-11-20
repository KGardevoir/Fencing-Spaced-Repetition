// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every schema version that has been installed on a device, by the identity
 * hash Room recorded for it.
 *
 * A released version never changes: Room checks the identity hash on open,
 * and a phone whose database has another hash under the same version
 * crashes on launch. Change the schema in a new version, and add the new
 * version's hash here once a build with it has been installed. The schema
 * files themselves are the committed exports in [DIR]; only version 18,
 * which was amended in place after builds were installed, keeps copies of
 * its earlier variants, in released-v18/.
 */
internal object ReleasedSchemas {
    const val DIR = "schemas/com.fencing.spacedrepetition.data.AppDatabase"

    val identityHashes = mapOf(
        18 to "6fed5d00559f72d11cefe21a22a693f5",
        19 to "f534fce417770dfbb4eeb513c89c826d",
        20 to "1e8f8f15232e4c624323f99863429121",
        21 to "8712aeb04c089740a1f228c30a6a1066",
        22 to "583e78a79e3aca6868a9fa14427a748e",
        // 23 is 21's schema under the number that joins the two lines.
        23 to "8712aeb04c089740a1f228c30a6a1066",
    )

    /** The committed export of [version]. */
    fun schema(version: Int) = File("$DIR/$version.json")

    /** An earlier, amended-away variant of version 18, by the commit that built it. */
    fun v18Variant(commit: String) = File("src/jvmTest/resources/released-v18/18-$commit.json")

    fun identityOf(file: File): String =
        Regex(""""identityHash"\s*:\s*"([0-9a-f]+)"""").find(file.readText())!!.groupValues[1]
}

class ReleasedSchemasTest {
    @Test
    fun `released schemas never change`() {
        ReleasedSchemas.identityHashes.forEach { (version, hash) ->
            assertEquals(hash, ReleasedSchemas.identityOf(ReleasedSchemas.schema(version)), "schema $version changed")
        }
    }
}
