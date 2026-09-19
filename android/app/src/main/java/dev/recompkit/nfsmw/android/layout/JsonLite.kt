package dev.recompkit_nfsmw.android.layout

/**
 * A minimal JSON reader/writer.
 *
 * The launcher's persistence files (the control layout, the settings, the
 * asset manifest) are JSON, and the kit's tooling reads them too, but the
 * launcher has no third-party JSON dependency: the format it writes is small
 * and stable, so a self-contained parser keeps the APK lean and the unit
 * tests runnable on a bare JVM (no android.jar).
 */
class JsonLiteException(message: String) : RuntimeException(message)

sealed class JsonValue {
    object Null : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val entries: LinkedHashMap<String, JsonValue>) : JsonValue()
}

object JsonLite {

    fun obj(vararg pairs: Pair<String, JsonValue>): JsonValue {
        val m = LinkedHashMap<String, JsonValue>()
        for (p in pairs) m[p.first] = p.second
        return JsonValue.Obj(m)
    }

    @SafeVarargs
    fun arr(vararg items: JsonValue): JsonValue = JsonValue.Arr(items.toList())

    fun str(s: String): JsonValue = JsonValue.Str(s)
    fun num(d: Double): JsonValue = JsonValue.Num(d)
    fun bool(b: Boolean): JsonValue = JsonValue.Bool(b)

    fun parse(text: String): JsonValue {
        val p = Parser(text)
        p.skipWs()
        val v = p.parseValue()
        p.skipWs()
        if (!p.atEnd()) throw JsonLiteException("Trailing characters at offset ${p.i}")
        return v
    }

    fun encode(value: JsonValue): String {
        val sb = StringBuilder()
        value.writeTo(sb)
        return sb.toString()
    }

    private class Parser(private val s: String) {
        var i = 0
        private val n = s.length

        fun atEnd() = i >= n

        fun skipWs() {
            while (i < n && s[i].isWhitespace()) i++
        }

        private fun peek(): Char =
            if (atEnd()) throw JsonLiteException("Unexpected end of input") else s[i]

        private fun expect(c: Char) {
            if (atEnd() || s[i] != c) throw JsonLiteException("Expected '$c' at offset $i")
            i++
        }

        private fun literal(word: String) {
            if (!s.startsWith(word, i)) throw JsonLiteException("Invalid literal at offset $i")
            i += word.length
        }

        fun parseValue(): JsonValue {
            skipWs()
            return when (val c = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> { literal("true"); JsonValue.Bool(true) }
                'f' -> { literal("false"); JsonValue.Bool(false) }
                'n' -> { literal("null"); JsonValue.Null }
                else -> parseNumber()
            }
        }

        private fun parseObject(): JsonValue {
            expect('{')
            val m = LinkedHashMap<String, JsonValue>()
            skipWs()
            if (!atEnd() && peek() == '}') {
                i++
                return JsonValue.Obj(m)
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                m[key] = parseValue()
                skipWs()
                when (val c = peek()) {
                    ',' -> i++
                    '}' -> { i++; return JsonValue.Obj(m) }
                    else -> throw JsonLiteException("Expected ',' or '}' at offset $i")
                }
            }
        }

        private fun parseArray(): JsonValue {
            expect('[')
            val list = ArrayList<JsonValue>()
            skipWs()
            if (!atEnd() && peek() == ']') {
                i++
                return JsonValue.Arr(list)
            }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (val c = peek()) {
                    ',' -> i++
                    ']' -> { i++; return JsonValue.Arr(list) }
                    else -> throw JsonLiteException("Expected ',' or ']' at offset $i")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonLiteException("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonLiteException("Unterminated escape")
                        val e = s[i++]
                        when (e) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\f')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > n) throw JsonLiteException("Bad unicode escape")
                                val hex = s.substring(i, i + 4)
                                i += 4
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonLiteException("Bad unicode escape '$hex'")
                                sb.append(code.toChar())
                            }
                            else -> throw JsonLiteException("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): JsonValue {
            val start = i
            if (!atEnd() && (peek() == '-' || peek() == '+')) i++
            while (!atEnd()) {
                val c = s[i]
                if (c.isDigit() || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') i++
                else break
            }
            if (start == i) throw JsonLiteException("Invalid value at offset $i")
            val txt = s.substring(start, i)
            val d = txt.toDoubleOrNull()
                ?: throw JsonLiteException("Invalid number '$txt' at offset $start")
            return JsonValue.Num(d)
        }
    }

    private fun JsonValue.writeTo(sb: StringBuilder) {
        when (this) {
            is JsonValue.Null -> sb.append("null")
            is JsonValue.Bool -> sb.append(value.toString())
            is JsonValue.Num -> {
                // Whole numbers stay whole: the layout file is human-readable.
                if (!value.isInfinite() && !value.isNaN() && value == value.toLong().toDouble()) {
                    sb.append(value.toLong())
                } else {
                    sb.append(value)
                }
            }
            is JsonValue.Str -> appendString(sb, value)
            is JsonValue.Arr -> {
                sb.append('[')
                items.forEachIndexed { idx, v ->
                    if (idx > 0) sb.append(',')
                    v.writeTo(sb)
                }
                sb.append(']')
            }
            is JsonValue.Obj -> {
                sb.append('{')
                entries.forEachIndexed { idx, (k, v) ->
                    if (idx > 0) sb.append(',')
                    appendString(sb, k)
                    sb.append(':')
                    v.writeTo(sb)
                }
                sb.append('}')
            }
        }
    }

    private fun appendString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\f' -> sb.append("\\f")
                else -> if (c.code < 0x20) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}
