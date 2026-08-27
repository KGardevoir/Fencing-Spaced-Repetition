// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

// A YAML reader and writer, sized for the import/export format and nothing
// else.
//
// Hand-written rather than depended on: :shared compiles for Android, the JVM
// and wasm, and the whole point of that module is that it needs the Kotlin
// standard library and nothing more. A YAML library that covered all three
// would be a fourth dependency to keep in step with the Kotlin version, for a
// document whose shape this app decides.
//
// What it covers is the block style an export writes -- mappings, sequences,
// plain and quoted scalars, and literal blocks -- plus enough tolerance for a
// file someone typed by hand: single quotes, folded blocks, flow collections
// on one line, comments, and a leading document marker. What it does not
// cover is anchors, aliases, tags, multiple documents in one file, and flow
// collections spread over several lines. Those are things this app never
// writes, and a file using them reports a parse error naming its line rather
// than importing something subtly wrong.
//
// Scalars come back as strings. Nothing here infers that 3 is a number and
// true a boolean, because the caller always knows which field it is reading
// and the guessing is where YAML's sharp edges are -- see [needsQuoting],
// which is the other half of the same decision.

/** A node of a parsed or constructed YAML document. */
sealed interface YamlNode {
    /** The 1-based line the node started on, or 0 for a node built in code. */
    val line: Int
}

/** A single value. Always text: see the note on typing at the top of the file. */
class YamlScalar(
    val text: String,
    /**
     * Written through untouched instead of being quoted when it needs to be.
     *
     * For the values this app produces itself as YAML rather than as text --
     * numbers and booleans -- so that `reps: 4` is a number to any other
     * reader of the file and `"4"` stays a string.
     */
    val plain: Boolean = false,
    override val line: Int = 0
) : YamlNode

/** An ordered list. [flow] asks for `[a, b]` on one line rather than a block. */
class YamlSequence(
    val items: List<YamlNode>,
    val flow: Boolean = false,
    override val line: Int = 0
) : YamlNode

/**
 * An ordered set of key/value pairs.
 *
 * A list rather than a Map because the order is the file's: an export that
 * reshuffled its keys between runs would make every diff of two backups
 * useless. Lookup by key is still constant time -- see [get].
 */
class YamlMapping(
    val entries: List<Pair<String, YamlNode>>,
    override val line: Int = 0
) : YamlNode {

    private val byKey: Map<String, YamlNode> = entries.toMap()

    operator fun get(key: String): YamlNode? = byKey[key]
}

// ==========================================================================
// Building
// ==========================================================================

/** A text value, quoted on the way out if YAML would otherwise misread it. */
fun yamlText(value: String): YamlScalar = YamlScalar(value)

fun yamlNumber(value: Int): YamlScalar = YamlScalar(value.toString(), plain = true)

fun yamlNumber(value: Long): YamlScalar = YamlScalar(value.toString(), plain = true)

fun yamlNumber(value: Double): YamlScalar = YamlScalar(value.toString(), plain = true)

fun yamlBoolean(value: Boolean): YamlScalar = YamlScalar(value.toString(), plain = true)

// ==========================================================================
// Reading what was parsed
// ==========================================================================

/** This node's text, or null if it is a collection. */
fun YamlNode?.textOrNull(): String? = (this as? YamlScalar)?.text

/**
 * This node's items.
 *
 * A single scalar counts as a list of one, so `groups: Footwork` reads the
 * same as `groups: [Footwork]` -- the shorthand someone writing the file by
 * hand reaches for first.
 */
fun YamlNode?.itemsOrEmpty(): List<YamlNode> = when (this) {
    is YamlSequence -> this.items
    is YamlScalar -> if (this.text.isEmpty()) emptyList() else listOf(this)
    else -> emptyList()
}

/** The mappings among this node's items, ignoring anything else. */
fun YamlNode?.mappingsOrEmpty(): List<YamlMapping> = itemsOrEmpty().filterIsInstance<YamlMapping>()

fun YamlMapping.text(key: String): String? = this[key].textOrNull()

/** The non-blank text items under [key]. */
fun YamlMapping.textList(key: String): List<String> =
    this[key].itemsOrEmpty().mapNotNull { it.textOrNull() }.filter { it.isNotBlank() }

fun YamlMapping.long(key: String): Long? = text(key)?.trim()?.toLongOrNull()

fun YamlMapping.int(key: String): Int? = text(key)?.trim()?.toIntOrNull()

fun YamlMapping.double(key: String): Double? = text(key)?.trim()?.toDoubleOrNull()

/** Reads a boolean, accepting the YAML 1.1 spellings a person might type. */
fun YamlMapping.boolean(key: String): Boolean? = when (text(key)?.trim()?.lowercase()) {
    "true", "yes", "on" -> true
    "false", "no", "off" -> false
    else -> null
}

// ==========================================================================
// Writing
// ==========================================================================

/** Writes this node as a block-style YAML document. */
fun YamlNode.writeYaml(out: Appendable) {
    YamlWriter(out).node(this, 0)
}

fun YamlNode.toYamlString(): String = StringBuilder().also { writeYaml(it) }.toString()

/**
 * Writes this node as one entry of a block sequence whose dashes sit at
 * [indent].
 *
 * The export writes its cards one at a time rather than building the whole
 * document and handing it over, so that a collection with a hundred
 * photographs in it never exists in memory at once. This is the piece that
 * lets it: the `cards:` key is written by hand, and each card comes through
 * here.
 */
fun YamlNode.writeYamlSequenceItem(out: Appendable, indent: Int = 0) {
    YamlWriter(out).sequenceItem(this, indent)
}

private class YamlWriter(private val out: Appendable) {

    /**
     * What the next line starts with, when it is not plain indentation.
     *
     * A block sequence entry is its dash followed by the first line of the
     * item, and everything after that line is indented as though the dash
     * were spaces. Holding the prefix here rather than passing it down means
     * the mapping and sequence writers do not have to know they are being
     * written as an entry of something else -- and nesting composes: a
     * sequence inside a sequence entry produces "- - ", which is exactly
     * right.
     */
    private var pending: String? = null

    fun node(node: YamlNode, indent: Int) {
        when (node) {
            is YamlMapping -> mapping(node, indent)
            is YamlSequence -> sequence(node, indent)
            is YamlScalar -> scalarLine(node, indent)
        }
    }

    fun mapping(map: YamlMapping, indent: Int) {
        if (map.entries.isEmpty()) {
            line(indent, "{}")
            return
        }
        map.entries.forEach { (key, value) -> entry(key, value, indent) }
    }

    fun sequence(seq: YamlSequence, indent: Int) {
        if (seq.items.isEmpty()) {
            line(indent, "[]")
            return
        }
        seq.items.forEach { sequenceItem(it, indent) }
    }

    fun sequenceItem(item: YamlNode, indent: Int) {
        dash(indent)
        when (item) {
            is YamlMapping -> mapping(item, indent + 2)
            is YamlSequence ->
                if (item.flow || item.items.isEmpty()) line(indent + 2, flow(item))
                else sequence(item, indent + 2)
            is YamlScalar -> scalarLine(item, indent + 2)
        }
    }

    private fun entry(key: String, value: YamlNode, indent: Int) {
        val name = scalarText(key)
        when {
            value is YamlScalar && isBlockScalar(value) -> {
                line(indent, "$name: |-")
                blockBody(value.text, indent + 2)
            }
            value is YamlScalar -> line(indent, "$name: ${scalar(value)}")
            value is YamlSequence && (value.flow || value.items.isEmpty()) ->
                line(indent, "$name: ${flow(value)}")
            value is YamlSequence -> {
                line(indent, "$name:")
                sequence(value, indent + 2)
            }
            value is YamlMapping && value.entries.isEmpty() -> line(indent, "$name: {}")
            else -> {
                line(indent, "$name:")
                mapping(value as YamlMapping, indent + 2)
            }
        }
    }

    private fun scalarLine(value: YamlScalar, indent: Int) {
        if (isBlockScalar(value)) {
            line(indent, "|-")
            blockBody(value.text, indent + 2)
        } else {
            line(indent, scalar(value))
        }
    }

    private fun line(indent: Int, text: String) {
        val prefix = pending
        if (prefix != null) {
            out.append(prefix)
            pending = null
        } else {
            out.append(indentation(indent))
        }
        out.append(text)
        out.append('\n')
    }

    private fun dash(indent: Int) {
        val prefix = pending
        pending = if (prefix != null) "$prefix- " else indentation(indent) + "- "
    }

    /**
     * The body of a `|-` block.
     *
     * An empty line is written empty rather than indented: trailing spaces on
     * a blank line are invisible in an editor and would come back as content.
     */
    private fun blockBody(text: String, indent: Int) {
        text.split("\n").forEach { bodyLine ->
            if (bodyLine.isEmpty()) {
                out.append('\n')
            } else {
                out.append(indentation(indent))
                out.append(bodyLine)
                out.append('\n')
            }
        }
    }

    private fun flow(seq: YamlSequence): String =
        seq.items.joinToString(", ", "[", "]") { item ->
            when (item) {
                is YamlScalar -> scalar(item)
                is YamlSequence -> flow(item)
                is YamlMapping -> item.entries
                    .joinToString(", ", "{", "}") { (k, v) ->
                        "${scalarText(k)}: ${if (v is YamlScalar) scalar(v) else "null"}"
                    }
            }
        }

    private fun scalar(value: YamlScalar): String =
        if (value.plain) value.text else scalarText(value.text)

    private fun indentation(width: Int): String = if (width <= 0) "" else " ".repeat(width)
}

/**
 * Whether a value is better written as a `|-` literal block than as a quoted
 * string.
 *
 * Only multi-line text is, and only when the block would come back exactly as
 * it went in. Leading whitespace on a line changes the indentation the reader
 * measures, trailing whitespace is silently dropped by most editors, and a
 * trailing newline is what `|-` exists to strip -- so any of those sends the
 * value down the quoted path instead, where every character is spelled out.
 */
private fun isBlockScalar(value: YamlScalar): Boolean {
    if (value.plain) return false
    val text = value.text
    if (!text.contains('\n')) return false
    if (text.endsWith("\n")) return false
    if (text.contains('\r')) return false
    return text.split("\n").none { bodyLine ->
        bodyLine.startsWith(" ") || bodyLine.startsWith("\t") ||
            bodyLine.endsWith(" ") || bodyLine.endsWith("\t") ||
            bodyLine.any { it.isISOControl() }
    }
}

private fun scalarText(text: String): String =
    if (needsQuoting(text)) yamlQuote(text) else text

/**
 * Whether a value has to be quoted to survive a round trip.
 *
 * The rule is deliberately eager. Everything this app writes as a plain
 * scalar is a number or a boolean it built itself, so the only cost of
 * quoting a string that did not strictly need it is a pair of quotes -- while
 * the cost of leaving one unquoted that did is an answer that reads back as
 * a number, a group called "no" that becomes false, or a file that will not
 * parse at all. So: anything not starting with a letter or an underscore is
 * quoted, as is anything holding a character YAML gives a meaning to.
 */
private fun needsQuoting(text: String): Boolean {
    if (text.isEmpty()) return true
    if (text != text.trim()) return true
    val first = text[0]
    if (!first.isLetter() && first != '_') return true
    if (text.any { it in YAML_INDICATORS || it.isISOControl() }) return true
    return text.lowercase() in YAML_RESERVED_WORDS
}

/**
 * Characters that mean something to YAML somewhere in a plain scalar, or
 * whose meaning depends on what follows them.
 *
 * ':' and '#' are only special before a space and at a word boundary
 * respectively, and the rest only at the start of a value -- but a plain
 * scalar holding any of them is one careful reading away from a bug, and the
 * alternative is a pair of quotes.
 */
private const val YAML_INDICATORS = "\"'\\:#,[]{}&*!|>%@`?\n\r\t"

private val YAML_RESERVED_WORDS =
    setOf("true", "false", "yes", "no", "on", "off", "null", "y", "n")

private fun yamlQuote(text: String): String = buildString {
    append('"')
    text.forEach { c ->
        when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c.code < 0x20 || c.code == 0x7F -> {
                append("\\x")
                append(c.code.toString(16).padStart(2, '0'))
            }
            else -> append(c)
        }
    }
    append('"')
}

// ==========================================================================
// Parsing
// ==========================================================================

/** A file that is not the YAML this app can read, and where it stopped being. */
class YamlException(
    message: String,
    /** The 1-based line the failure was found on, or 0 if it has no line. */
    val line: Int
) : IllegalArgumentException(if (line > 0) "Line $line: $message" else message)

/** Parses a whole document, or null if there is nothing in it but comments. */
fun parseYaml(text: String): YamlNode? = parseYaml(text.split("\n"))

fun parseYaml(lines: List<String>): YamlNode? = YamlParser(lines).parseDocument()

private class YamlParser(source: List<String>) {

    /**
     * The file, with the line endings and the byte-order mark taken off.
     *
     * Mutable because of the one thing block sequences do that an
     * indentation-driven parser cannot take at face value: `- key: value`
     * puts a mapping at a column its own line does not start at. Blanking the
     * dash turns that line into an ordinary mapping line at the right column,
     * and the mapping parser needs to know nothing about how it got there.
     */
    private val lines: MutableList<String> = source.mapIndexed { index, raw ->
        val withoutCarriageReturn = raw.removeSuffix("\r")
        if (index == 0) withoutCarriageReturn.removePrefix("\uFEFF") else withoutCarriageReturn
    }.toMutableList()

    private var at = 0

    fun parseDocument(): YamlNode? {
        skipIgnorable()
        while (at < lines.size && (lines[at].trim().startsWith("%") || lines[at].trim() == "---")) {
            at++
            skipIgnorable()
        }
        if (at >= lines.size || isDocumentBreak(at)) return null
        return parseBlock(indentOf(at))
    }

    // ---------- collections ----------

    private fun parseBlock(indent: Int): YamlNode {
        skipIgnorable()
        if (at >= lines.size) return YamlScalar("")
        return when {
            isSequenceEntry(at, indent) -> parseSequence(indent)
            keySeparator(lines[at], indent) != null -> parseMapping(indent)
            else -> parsePlainBlockScalar(indent)
        }
    }

    private fun parseMapping(indent: Int): YamlMapping {
        val startLine = at + 1
        val entries = mutableListOf<Pair<String, YamlNode>>()
        while (true) {
            skipIgnorable()
            if (at >= lines.size || isDocumentBreak(at)) break
            val currentIndent = indentOf(at)
            if (currentIndent < indent) break
            if (currentIndent > indent) throw YamlException("unexpected indentation", at + 1)
            if (isSequenceEntry(at, currentIndent)) break

            val lineNumber = at + 1
            val current = lines[at]
            val separator = keySeparator(current, indent)
                ?: throw YamlException("expected \"key: value\"", lineNumber)
            val key = readKey(current, indent, separator, lineNumber)
            val rest = current.substring(separator + 1)
            at++
            entries.add(key to parseValue(rest, indent, lineNumber))
        }
        return YamlMapping(entries, startLine)
    }

    private fun parseSequence(indent: Int): YamlSequence {
        val startLine = at + 1
        val items = mutableListOf<YamlNode>()
        while (true) {
            skipIgnorable()
            if (at >= lines.size || isDocumentBreak(at)) break
            val currentIndent = indentOf(at)
            if (currentIndent < indent) break
            if (currentIndent > indent) throw YamlException("unexpected indentation", at + 1)
            if (!isSequenceEntry(at, currentIndent)) break

            val lineNumber = at + 1
            val current = lines[at]
            var column = indent + 1
            while (column < current.length && current[column] == ' ') column++
            val remainder = if (column < current.length) current.substring(column) else ""

            when {
                remainder.isEmpty() || remainder.startsWith("#") -> {
                    at++
                    items.add(parseNestedValue(indent))
                }
                // "- - a": a sequence whose first item starts on the same line.
                remainder == "-" || remainder.startsWith("- ") -> {
                    lines[at] = " ".repeat(column) + remainder
                    items.add(parseSequence(column))
                }
                // "- key: value": a mapping whose first entry starts on the same line.
                keySeparator(current, column) != null -> {
                    lines[at] = " ".repeat(column) + remainder
                    items.add(parseMapping(column))
                }
                else -> {
                    at++
                    items.add(inlineValue(remainder.trim(), indent, lineNumber))
                }
            }
        }
        return YamlSequence(items, line = startLine)
    }

    // ---------- values ----------

    private fun parseValue(rest: String, parentIndent: Int, lineNumber: Int): YamlNode {
        val trimmed = rest.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return parseNestedValue(parentIndent)
        return inlineValue(trimmed, parentIndent, lineNumber)
    }

    /**
     * The value of a key that had nothing after its colon: whatever is
     * indented under it, or nothing at all.
     *
     * A block sequence is allowed to sit at its key's own column, which is
     * the style most people write by hand, so that case is taken as well as
     * the indented one.
     */
    private fun parseNestedValue(parentIndent: Int): YamlNode {
        skipIgnorable()
        if (at >= lines.size || isDocumentBreak(at)) return YamlScalar("")
        val currentIndent = indentOf(at)
        if (currentIndent > parentIndent) return parseBlock(currentIndent)
        if (currentIndent == parentIndent && isSequenceEntry(at, currentIndent)) {
            return parseSequence(currentIndent)
        }
        return YamlScalar("")
    }

    private fun inlineValue(trimmed: String, parentIndent: Int, lineNumber: Int): YamlNode =
        when (trimmed[0]) {
            '|', '>' -> parseBlockScalar(trimmed, parentIndent, lineNumber)
            '[', '{' -> FlowParser(trimmed, lineNumber).parse()
            '"', '\'' -> quotedValue(trimmed, lineNumber)
            else -> YamlScalar(plainValue(trimmed, parentIndent), line = lineNumber)
        }

    /**
     * A quoted value, which YAML lets run past the end of its line.
     *
     * An export never writes one that does -- a multi-line answer goes out as
     * a `|-` block, and anything else is quoted on one line with its breaks
     * spelled out -- but other writers wrap long strings by default, so a
     * file from one of them would otherwise stop the import dead.
     */
    private fun quotedValue(first: String, lineNumber: Int): YamlScalar {
        var token = first
        while (scanQuoted(token, 0) == null) {
            if (at >= lines.size) throw YamlException("unterminated quotes", lineNumber)
            token += "\n" + lines[at]
            at++
        }
        val end = scanQuoted(token, 0) ?: throw YamlException("unterminated quotes", lineNumber)
        val folded = foldQuotedBreaks(token.substring(0, end))
        return YamlScalar(
            if (first[0] == '"') {
                decodeDoubleQuoted(folded, lineNumber)
            } else {
                decodeSingleQuoted(folded, lineNumber)
            },
            line = lineNumber
        )
    }

    /**
     * An unquoted value, and any more-indented lines continuing it.
     *
     * The continuation stops at anything that could be structure rather than
     * text -- a dash, a `key:` -- so that a genuinely misindented line still
     * reports itself as one instead of being swallowed into the value above.
     */
    private fun plainValue(first: String, parentIndent: Int): String {
        val segments = mutableListOf(stripInlineComment(first).trim())
        while (at < lines.size) {
            val current = lines[at]
            if (current.isBlank() || isDocumentBreak(at)) break
            val currentIndent = indentOf(at)
            if (currentIndent <= parentIndent) break
            if (isSequenceEntry(at, currentIndent)) break
            if (keySeparator(current, currentIndent) != null) break
            val content = stripInlineComment(current.substring(currentIndent)).trim()
            if (content.isEmpty()) break
            segments.add(content)
            at++
        }
        val text = segments.filter { it.isNotEmpty() }.joinToString(" ")
        return if (text == "~" || text.equals("null", ignoreCase = true)) "" else text
    }

    /**
     * A `|`, `|-`, `>` or `>2` block, read to the first line that dedents.
     *
     * Exports only ever write `|-`, which keeps the text exactly. The rest is
     * here so that a file written by hand or by another tool imports.
     */
    private fun parseBlockScalar(header: String, parentIndent: Int, lineNumber: Int): YamlScalar {
        val indicators = header.substringBefore(' ').substringBefore('#')
        val literal = indicators[0] == '|'
        var chomping = CLIP
        var explicitIndent = 0
        indicators.drop(1).forEach { indicator ->
            when {
                indicator == '-' -> chomping = STRIP
                indicator == '+' -> chomping = KEEP
                indicator.isDigit() && indicator != '0' -> explicitIndent = indicator - '0'
                else -> throw YamlException(
                    "unrecognised block scalar header \"$indicators\"",
                    lineNumber
                )
            }
        }

        var contentIndent = if (explicitIndent > 0) parentIndent + explicitIndent else -1
        val body = mutableListOf<String>()
        while (at < lines.size) {
            val current = lines[at]
            if (current.isBlank()) {
                body.add("")
                at++
                continue
            }
            val currentIndent = indentOf(at)
            if (contentIndent < 0) {
                if (currentIndent <= parentIndent) break
                contentIndent = currentIndent
            }
            if (currentIndent < contentIndent) break
            body.add(current.substring(contentIndent))
            at++
        }
        if (chomping != KEEP) {
            while (body.isNotEmpty() && body.last().isEmpty()) body.removeAt(body.size - 1)
        }

        val text = if (literal) body.joinToString("\n") else foldLines(body)
        return YamlScalar(
            when {
                chomping == STRIP -> text
                text.isEmpty() -> ""
                else -> "$text\n"
            },
            line = lineNumber
        )
    }

    /** A value spread over several unindented lines, joined the way `>` joins. */
    private fun parsePlainBlockScalar(indent: Int): YamlScalar {
        val startLine = at + 1
        val parts = mutableListOf<String>()
        while (at < lines.size) {
            if (lines[at].isBlank() || isDocumentBreak(at)) break
            val currentIndent = indentOf(at)
            if (currentIndent < indent) break
            val content = stripInlineComment(lines[at].substring(currentIndent)).trim()
            if (content.isEmpty()) break
            parts.add(content)
            at++
        }
        if (parts.isEmpty()) throw YamlException("expected a value", startLine)
        return YamlScalar(parts.joinToString(" "), line = startLine)
    }

    private fun foldLines(body: List<String>): String {
        val folded = StringBuilder()
        var joinable = false
        body.forEach { bodyLine ->
            when {
                bodyLine.isBlank() -> {
                    folded.append('\n')
                    joinable = false
                }
                bodyLine.startsWith(" ") -> {
                    if (joinable) folded.append('\n')
                    folded.append(bodyLine)
                    joinable = false
                }
                else -> {
                    if (joinable) folded.append(' ')
                    folded.append(bodyLine)
                    joinable = true
                }
            }
        }
        return folded.toString()
    }

    // ---------- line shapes ----------

    private fun skipIgnorable() {
        while (at < lines.size) {
            val trimmed = lines[at].trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) at++ else break
        }
    }

    private fun indentOf(index: Int): Int {
        val current = lines[index]
        var width = 0
        while (width < current.length && current[width] == ' ') width++
        if (width < current.length && current[width] == '\t') {
            throw YamlException("YAML cannot be indented with tabs; use spaces", index + 1)
        }
        return width
    }

    private fun isDocumentBreak(index: Int): Boolean {
        val current = lines[index]
        return current == "---" || current == "..." ||
            current.startsWith("--- ") || current.startsWith("... ")
    }

    private fun isSequenceEntry(index: Int, indent: Int): Boolean {
        val current = lines[index]
        if (indent >= current.length || current[indent] != '-') return false
        return indent + 1 == current.length || current[indent + 1] == ' '
    }

    /**
     * Where the colon that separates a key from its value is, or null if this
     * line does not hold one.
     *
     * The first such colon wins, so `answer: a: b` is the key `answer` with
     * the value `a: b` -- which is what a reader of the file would say it is.
     */
    private fun keySeparator(current: String, from: Int): Int? {
        if (from >= current.length) return null
        var index = from
        if (current[from] == '"' || current[from] == '\'') {
            val afterQuote = scanQuoted(current, from) ?: return null
            index = afterQuote
            while (index < current.length && current[index] == ' ') index++
            return if (index < current.length && current[index] == ':') index else null
        }
        while (index < current.length) {
            val c = current[index]
            if (c == ':' && (index + 1 == current.length || current[index + 1] == ' ')) return index
            if (c == '#' && index > from && current[index - 1] == ' ') return null
            index++
        }
        return null
    }

    private fun readKey(current: String, indent: Int, separator: Int, lineNumber: Int): String {
        val raw = current.substring(indent, separator).trim()
        return when {
            raw.startsWith("\"") -> decodeDoubleQuoted(raw, lineNumber)
            raw.startsWith("'") -> decodeSingleQuoted(raw, lineNumber)
            else -> raw
        }
    }

    private companion object {
        const val STRIP = '-'
        const val CLIP = ' '
        const val KEEP = '+'
    }
}

/**
 * A flow collection -- `[a, b]` or `{a: 1}` -- on one line.
 *
 * Never written by an export, which is all block style, but short enough to
 * support that a hand-written `groups: [Footwork, Drills]` should not be a
 * parse error. A flow collection spread over several lines is not supported.
 */
private class FlowParser(private val source: String, private val lineNumber: Int) {

    private var at = 0

    fun parse(): YamlNode = node()

    private fun node(): YamlNode {
        skipSpaces()
        if (at >= source.length) throw YamlException("unexpected end of value", lineNumber)
        return when (source[at]) {
            '[' -> sequence()
            '{' -> mapping()
            '"' -> YamlScalar(readQuoted(doubleQuoted = true), line = lineNumber)
            '\'' -> YamlScalar(readQuoted(doubleQuoted = false), line = lineNumber)
            else -> YamlScalar(readPlain(":,[]{}"), line = lineNumber)
        }
    }

    private fun sequence(): YamlSequence {
        at++
        val items = mutableListOf<YamlNode>()
        skipSpaces()
        if (at < source.length && source[at] == ']') {
            at++
            return YamlSequence(items, flow = true, line = lineNumber)
        }
        while (true) {
            items.add(node())
            skipSpaces()
            if (at >= source.length) throw YamlException("unterminated \"[\"", lineNumber)
            when (source[at]) {
                ',' -> at++
                ']' -> {
                    at++
                    return YamlSequence(items, flow = true, line = lineNumber)
                }
                else -> throw YamlException("unexpected \"${source[at]}\" in a list", lineNumber)
            }
        }
    }

    private fun mapping(): YamlMapping {
        at++
        val entries = mutableListOf<Pair<String, YamlNode>>()
        skipSpaces()
        if (at < source.length && source[at] == '}') {
            at++
            return YamlMapping(entries, lineNumber)
        }
        while (true) {
            skipSpaces()
            if (at >= source.length) throw YamlException("unterminated \"{\"", lineNumber)
            val key = when (source[at]) {
                '"' -> readQuoted(doubleQuoted = true)
                '\'' -> readQuoted(doubleQuoted = false)
                else -> readPlain(":,[]{}")
            }
            skipSpaces()
            if (at >= source.length || source[at] != ':') {
                throw YamlException("expected \":\" after \"$key\"", lineNumber)
            }
            at++
            entries.add(key to node())
            skipSpaces()
            if (at >= source.length) throw YamlException("unterminated \"{\"", lineNumber)
            when (source[at]) {
                ',' -> at++
                '}' -> {
                    at++
                    return YamlMapping(entries, lineNumber)
                }
                else -> throw YamlException("unexpected \"${source[at]}\" in a map", lineNumber)
            }
        }
    }

    private fun readQuoted(doubleQuoted: Boolean): String {
        val end = scanQuoted(source, at) ?: throw YamlException("unterminated quotes", lineNumber)
        val token = source.substring(at, end)
        at = end
        return if (doubleQuoted) {
            decodeDoubleQuoted(token, lineNumber)
        } else {
            decodeSingleQuoted(token, lineNumber)
        }
    }

    private fun readPlain(stopAt: String): String {
        val start = at
        while (at < source.length && source[at] !in stopAt) at++
        return source.substring(start, at).trim()
    }

    private fun skipSpaces() {
        while (at < source.length && (source[at] == ' ' || source[at] == '\t')) at++
    }
}

// ==========================================================================
// Scalar text, shared by both parsers
// ==========================================================================

/** The index just past the closing quote of the string starting at [start]. */
private fun scanQuoted(current: String, start: Int): Int? {
    val quote = current[start]
    var index = start + 1
    while (index < current.length) {
        val c = current[index]
        if (quote == '"' && c == '\\') {
            index += 2
            continue
        }
        if (c == quote) {
            if (quote == '\'' && index + 1 < current.length && current[index + 1] == '\'') {
                index += 2
                continue
            }
            return index + 1
        }
        index++
    }
    return null
}

/**
 * The line breaks inside a quoted scalar, folded the way YAML folds them.
 *
 * One break is a space, a run of them is one fewer newline, the whitespace
 * around each break goes, and a break escaped with a trailing backslash --
 * how a long word gets wrapped -- joins the two halves with nothing between.
 */
private fun foldQuotedBreaks(token: String): String {
    if (!token.contains('\n')) return token
    val segments = token.split("\n")
    val folded = StringBuilder(segments[0].trimEnd())
    var breaks = 0
    for (index in 1 until segments.size) {
        val segment = segments[index].trim()
        if (segment.isEmpty()) {
            breaks++
            continue
        }
        when {
            breaks == 0 && endsWithEscape(folded) -> folded.setLength(folded.length - 1)
            breaks > 0 -> folded.append("\n".repeat(breaks))
            else -> folded.append(' ')
        }
        folded.append(segment)
        breaks = 0
    }
    return folded.toString()
}

/** Whether the last backslash of [text] escapes what comes next. */
private fun endsWithEscape(text: CharSequence): Boolean {
    var backslashes = 0
    var index = text.length - 1
    while (index >= 0 && text[index] == '\\') {
        backslashes++
        index--
    }
    return backslashes % 2 == 1
}

private fun stripInlineComment(text: String): String {
    text.forEachIndexed { index, c ->
        if (c == '#' && (index == 0 || text[index - 1] == ' ' || text[index - 1] == '\t')) {
            return text.substring(0, index)
        }
    }
    return text
}

private fun decodeDoubleQuoted(text: String, lineNumber: Int): String {
    val decoded = StringBuilder()
    var index = 1
    while (index < text.length) {
        val c = text[index]
        if (c == '"') return decoded.toString()
        if (c != '\\') {
            decoded.append(c)
            index++
            continue
        }
        index++
        if (index >= text.length) break
        when (val escape = text[index]) {
            'n' -> decoded.append('\n')
            't' -> decoded.append('\t')
            'r' -> decoded.append('\r')
            '0' -> decoded.append('\u0000')
            'a' -> decoded.append('\u0007')
            'b' -> decoded.append('\b')
            'f' -> decoded.append('\u000C')
            'v' -> decoded.append('\u000B')
            'e' -> decoded.append('\u001B')
            'N' -> decoded.append('\u0085')
            '_' -> decoded.append('\u00A0')
            'L' -> decoded.append('\u2028')
            'P' -> decoded.append('\u2029')
            '\\', '"', '/', '\'', ' ' -> decoded.append(escape)
            'x' -> {
                decoded.append(Char(hexEscape(text, index + 1, 2, lineNumber)))
                index += 2
            }
            'u' -> {
                decoded.append(Char(hexEscape(text, index + 1, 4, lineNumber)))
                index += 4
            }
            'U' -> {
                appendCodePoint(decoded, hexEscape(text, index + 1, 8, lineNumber))
                index += 8
            }
            else -> throw YamlException("unrecognised escape \"\\$escape\"", lineNumber)
        }
        index++
    }
    throw YamlException("unterminated quotes", lineNumber)
}

private fun decodeSingleQuoted(text: String, lineNumber: Int): String {
    val decoded = StringBuilder()
    var index = 1
    while (index < text.length) {
        val c = text[index]
        if (c == '\'') {
            if (index + 1 < text.length && text[index + 1] == '\'') {
                decoded.append('\'')
                index += 2
                continue
            }
            return decoded.toString()
        }
        decoded.append(c)
        index++
    }
    throw YamlException("unterminated quotes", lineNumber)
}

private fun hexEscape(text: String, start: Int, digits: Int, lineNumber: Int): Int {
    if (start + digits > text.length) throw YamlException("truncated escape", lineNumber)
    return text.substring(start, start + digits).toIntOrNull(16)
        ?: throw YamlException("invalid escape \"${text.substring(start, start + digits)}\"", lineNumber)
}

private fun appendCodePoint(out: StringBuilder, code: Int) {
    if (code <= 0xFFFF) {
        out.append(Char(code))
        return
    }
    val offset = code - 0x10000
    out.append(Char(0xD800 + (offset shr 10)))
    out.append(Char(0xDC00 + (offset and 0x3FF)))
}
