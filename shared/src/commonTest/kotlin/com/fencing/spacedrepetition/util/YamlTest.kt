// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The YAML reader and writer underneath the export format.
 *
 * Two things are being pinned down here. The first is that anything written
 * comes back unchanged -- the awkward-strings test below is the one that
 * matters, because every character YAML gives a meaning to is a way for an
 * answer to come back as a number, as a boolean, or not at all. The second is
 * that a file someone typed by hand parses: block sequences at either
 * indentation, flow lists, folded and literal blocks, quoted strings that run
 * over a line, comments.
 */
class YamlTest {

    // ==================== round trip ====================

    /**
     * Every character YAML treats as punctuation somewhere, plus the words it
     * reads as something other than text.
     */
    private val awkward = listOf(
        "plain text",
        "",
        " ",
        "  leading and trailing  ",
        "has: a colon",
        "colon:no space",
        "has # a hash",
        "trailing hash #",
        "\"double quoted\"",
        "'single quoted'",
        "back\\slash",
        "- dash first",
        "-dash",
        "? question first",
        "42",
        "0",
        "3.14",
        "-1e9",
        "true",
        "True",
        "false",
        "yes",
        "no",
        "on",
        "off",
        "null",
        "~",
        "y",
        "n",
        "line one\nline two",
        "line one\n\nline three",
        "trailing newline\n",
        "leading newline\ntext",
        "  indented first\nsecond",
        "text\n  indented second",
        "trailing space \nsecond",
        "tab\there",
        "carriage\r\nreturn",
        "é 日本語 🤺",
        "[bracketed]",
        "{braced}",
        "a, b, c",
        "*star",
        "&anchor",
        "!tag",
        "|pipe",
        ">gt",
        "%percent",
        "@at",
        "`backtick`",
        "2026-08-27",
        "12:30",
        "one\ntwo\nthree\nfour\nfive"
    )

    @Test
    fun `every awkward value survives a round trip`() {
        awkward.forEach { value ->
            val document = YamlMapping(listOf("value" to yamlText(value)))
            val text = document.toYamlString()
            val parsed = parseYaml(text)
            assertTrue(parsed is YamlMapping, "not a mapping for <${escape(value)}>:\n$text")
            assertEquals(value, parsed.text("value"), "round trip failed for <${escape(value)}>:\n$text")
        }
    }

    @Test
    fun `awkward values survive as sequence items and as keys`() {
        awkward.filter { it.isNotBlank() && !it.contains('\n') }.forEach { value ->
            val document = YamlMapping(
                listOf(
                    "items" to YamlSequence(listOf(yamlText(value), yamlText(value))),
                    value to yamlText("keyed")
                )
            )
            val text = document.toYamlString()
            val parsed = parseYaml(text)
            assertTrue(parsed is YamlMapping, "not a mapping for <${escape(value)}>:\n$text")
            assertEquals(listOf(value, value), parsed.textList("items"), text)
            assertEquals("keyed", parsed.text(value), text)
        }
    }

    @Test
    fun `numbers and booleans are written as numbers and booleans`() {
        val document = YamlMapping(
            listOf(
                "count" to yamlNumber(4),
                "when" to yamlNumber(1_700_000_000_000L),
                "ratio" to yamlNumber(1.25),
                "enabled" to yamlBoolean(true),
                "text" to yamlText("4")
            )
        )
        val text = document.toYamlString()
        assertEquals(
            "count: 4\nwhen: 1700000000000\nratio: 1.25\nenabled: true\ntext: \"4\"\n",
            text
        )
    }

    @Test
    fun `multi-line text is written as a literal block`() {
        val text = YamlMapping(listOf("answer" to yamlText("first\nsecond"))).toYamlString()
        assertEquals("answer: |-\n  first\n  second\n", text)
    }

    @Test
    fun `text a literal block could not hold exactly is quoted instead`() {
        // A trailing newline, which "|-" strips, and a line with trailing
        // whitespace, which an editor would eat.
        assertEquals(
            "answer: \"first\\nsecond\\n\"\n",
            YamlMapping(listOf("answer" to yamlText("first\nsecond\n"))).toYamlString()
        )
        assertEquals(
            "answer: \"first \\nsecond\"\n",
            YamlMapping(listOf("answer" to yamlText("first \nsecond"))).toYamlString()
        )
    }

    @Test
    fun `nested collections are written as blocks and flow lists as asked`() {
        val document = YamlMapping(
            listOf(
                "groups" to YamlSequence(listOf(yamlText("Foil"), yamlText("Epee")), flow = true),
                "images" to YamlSequence(listOf(yamlText("aaa"), yamlText("bbb"))),
                "state" to YamlMapping(listOf("reps" to yamlNumber(2))),
                "cards" to YamlSequence(
                    listOf(
                        YamlMapping(
                            listOf(
                                "question" to yamlText("Q"),
                                "tags" to YamlSequence(listOf(yamlText("a")))
                            )
                        )
                    )
                ),
                "nothing" to YamlSequence(emptyList()),
                "empty" to YamlMapping(emptyList())
            )
        )
        assertEquals(
            """
            groups: [Foil, Epee]
            images:
              - aaa
              - bbb
            state:
              reps: 2
            cards:
              - question: Q
                tags:
                  - a
            nothing: []
            empty: {}

            """.trimIndent(),
            document.toYamlString()
        )
    }

    // ==================== reading ====================

    @Test
    fun `a block sequence may sit at its key's own column`() {
        val parsed = parseYaml(
            """
            cards:
            - question: One
            - question: Two
            """.trimIndent()
        )
        assertTrue(parsed is YamlMapping)
        assertEquals(2, parsed["cards"].mappingsOrEmpty().size)
        assertEquals("Two", parsed["cards"].mappingsOrEmpty()[1].text("question"))
    }

    @Test
    fun `comments and a document marker are ignored`() {
        val parsed = parseYaml(
            """
            # a heading
            ---
            version: 5  # trailing comment
            # another
            name: Kit
            """.trimIndent()
        )
        assertTrue(parsed is YamlMapping)
        assertEquals("5", parsed.text("version"))
        assertEquals("Kit", parsed.text("name"))
    }

    @Test
    fun `a hash that is not preceded by a space is part of the value`() {
        val parsed = parseYaml("language: C#")
        assertEquals("C#", (parsed as YamlMapping).text("language"))
    }

    @Test
    fun `flow collections are read`() {
        val parsed = parseYaml("""groups: [Foil, "Epee, sabre", 'a''b']""")
        assertEquals(listOf("Foil", "Epee, sabre", "a'b"), (parsed as YamlMapping).textList("groups"))

        val nested = parseYaml("""where: {group: Foil, tags: [a, b]}""")
        val map = (nested as YamlMapping)["where"] as YamlMapping
        assertEquals("Foil", map.text("group"))
        assertEquals(listOf("a", "b"), map.textList("tags"))
    }

    @Test
    fun `a single scalar counts as a list of one`() {
        val parsed = parseYaml("groups: Foil")
        assertEquals(listOf("Foil"), (parsed as YamlMapping).textList("groups"))
    }

    @Test
    fun `folded and kept blocks are read`() {
        val parsed = parseYaml(
            """
            folded: >-
              one
              two
            kept: |+
              body

            clipped: |
              body
            """.trimIndent()
        )
        assertTrue(parsed is YamlMapping)
        assertEquals("one two", parsed.text("folded"))
        assertEquals("body\n", parsed.text("clipped"))
    }

    @Test
    fun `a quoted value may run over several lines`() {
        val parsed = parseYaml(
            "answer: \"one\n  two\n\n  three\"\nnext: here"
        )
        assertTrue(parsed is YamlMapping)
        assertEquals("one two\nthree", parsed.text("answer"))
        assertEquals("here", parsed.text("next"))
    }

    @Test
    fun `an unquoted value may run over several lines`() {
        val parsed = parseYaml(
            """
            answer: one
              two
            next: here
            """.trimIndent()
        )
        assertTrue(parsed is YamlMapping)
        assertEquals("one two", parsed.text("answer"))
        assertEquals("here", parsed.text("next"))
    }

    @Test
    fun `null and empty values read as empty text`() {
        val parsed = parseYaml("a: ~\nb: null\nc:\nd: \"\"")
        assertTrue(parsed is YamlMapping)
        assertEquals("", parsed.text("a"))
        assertEquals("", parsed.text("b"))
        assertEquals("", parsed.text("c"))
        assertEquals("", parsed.text("d"))
    }

    @Test
    fun `escapes in a quoted value are decoded`() {
        val parsed = parseYaml("""a: "tab\there and \u00e9 and \x41 and \\ and \"" """)
        assertEquals("tab\there and é and A and \\ and \"", (parsed as YamlMapping).text("a"))
    }

    @Test
    fun `booleans are read in the spellings a person might type`() {
        val parsed = parseYaml("a: yes\nb: OFF\nc: true\nd: maybe") as YamlMapping
        assertEquals(true, parsed.boolean("a"))
        assertEquals(false, parsed.boolean("b"))
        assertEquals(true, parsed.boolean("c"))
        assertNull(parsed.boolean("d"))
    }

    @Test
    fun `numbers are read as numbers`() {
        val parsed = parseYaml("a: 4\nb: 1700000000000\nc: 1.25\nd: nope") as YamlMapping
        assertEquals(4, parsed.int("a"))
        assertEquals(1_700_000_000_000L, parsed.long("b"))
        assertEquals(1.25, parsed.double("c"))
        assertNull(parsed.int("d"))
    }

    @Test
    fun `a file of nothing but comments is no document at all`() {
        assertNull(parseYaml("# one\n\n# two"))
        assertNull(parseYaml(""))
    }

    @Test
    fun `keys keep the order the file gave them`() {
        val parsed = parseYaml("z: 1\na: 2\nm: 3") as YamlMapping
        assertEquals(listOf("z", "a", "m"), parsed.entries.map { it.first })
    }

    // ==================== failures ====================

    @Test
    fun `a tab used as indentation is refused, with its line`() {
        val failure = assertFailsWith<YamlException> { parseYaml("cards:\n\t- question: x") }
        assertEquals(2, failure.line)
        assertTrue(failure.message!!.contains("tabs"), failure.message!!)
    }

    @Test
    fun `an over-indented line is refused, with its line`() {
        val failure = assertFailsWith<YamlException> { parseYaml("a: 1\nb: 2\n    c: 3") }
        assertEquals(3, failure.line)
    }

    @Test
    fun `an unterminated quote is refused`() {
        assertFailsWith<YamlException> { parseYaml("a: \"never closed") }
    }

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
