package dev.recompkit_nfsmw.android.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class JsonLiteTest {

    @Test
    fun roundTripNested() {
        val v = JsonLite.obj(
            "a" to JsonLite.num(1.0),
            "b" to JsonLite.str("hi \"there\"\n\\end"),
            "c" to JsonLite.bool(true),
            "d" to JsonLite.arr(JsonLite.num(0.5), JsonLite.obj("x" to JsonLite.str("y"))),
            "e" to JsonValue.Null
        )
        val text = JsonLite.encode(v)
        val back = JsonLite.parse(text)
        assertEquals(v, back)
    }

    @Test
    fun wholeNumbersStayWhole() {
        assertEquals(1.0, (JsonLite.parse(JsonLite.encode(JsonLite.num(1.0))) as JsonValue.Num).value, 0.0)
        assertTrue("\"x\":3" in JsonLite.encode(JsonLite.obj("x" to JsonLite.num(3.0))))
    }

    @Test
    fun fractionsSurvive() {
        val v = JsonLite.parse(JsonLite.encode(JsonLite.num(0.87))) as JsonValue.Num
        assertEquals(0.87, v.value, 1e-12)
    }

    @Test
    fun parseEscapes() {
        assertEquals("a\nb\tc\"d\\e", (JsonLite.parse("\"a\\nb\\tc\\\"d\\\\e\"") as JsonValue.Str).value)
        assertEquals("A", (JsonLite.parse("\"\\u0041\"") as JsonValue.Str).value)
        assertEquals("\u0417", (JsonLite.parse("\"\\u0417\"") as JsonValue.Str).value)
    }

    @Test
    fun parseNumbers() {
        assertEquals(2.5, (JsonLite.parse("2.5") as JsonValue.Num).value, 0.0)
        assertEquals(-1.0, (JsonLite.parse("-1") as JsonValue.Num).value, 0.0)
        assertEquals(1.5e3, (JsonLite.parse("1.5e3") as JsonValue.Num).value, 1e-9)
    }

    @Test
    fun emptyContainers() {
        assertEquals(JsonLite.arr(), JsonLite.parse("[]"))
        assertEquals(JsonLite.obj(), JsonLite.parse("{}"))
    }

    @Test
    fun toleratesWhitespace() {
        assertEquals(JsonLite.obj("a" to JsonLite.bool(true)), JsonLite.parse("{\n \"a\" : true \n}"))
    }

    @Test
    fun rejectsTrailing() {
        assertThrows(JsonLiteException::class.java) { JsonLite.parse("{} x") }
    }

    @Test
    fun rejectsUnterminatedString() {
        assertThrows(JsonLiteException::class.java) { JsonLite.parse("\"abc") }
    }

    @Test
    fun rejectsBadLiteral() {
        assertThrows(JsonLiteException::class.java) { JsonLite.parse("tru") }
    }

    @Test
    fun rejectsBadNumber() {
        assertThrows(JsonLiteException::class.java) { JsonLite.parse("1.2.3") }
    }

    @Test
    fun rejectsMissingColon() {
        assertThrows(JsonLiteException::class.java) { JsonLite.parse("{\"a\" 1}") }
    }
}
