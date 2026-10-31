package io.github.ammaralghifary.blueprint

import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Serializable
data class AdvText(val text: String, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class AdvColumn(val children: List<SduiComponent> = emptyList(), override val modifier: SduiModifier? = null) : SduiComponent()

private val advBlueprint = Blueprint(
    sduiRegistry {
        component<AdvText>("text") { _, _, _ -> }
        component<AdvColumn>("column") { _, _, _ -> }
    },
)

// Hostile payloads: decodeScreen promises never to throw, so every case must come back as a screen.
class DecodeAdversarialTest {

    private fun nested(depth: Int): String =
        buildString {
            repeat(depth) { append("""{"type":"column","children":[""") }
            append("""{"type":"text","text":"leaf"}""")
            repeat(depth) { append("]}") }
        }

    private fun assertNeverThrows(label: String, payload: String): SduiScreen =
        try {
            advBlueprint.decodeScreen(payload)
        } catch (t: Throwable) {
            throw AssertionError("$label escaped decodeScreen as ${t::class.simpleName}: ${t.message?.take(120)}", t)
        }

    @Test
    fun aTreeWellWithinTheLimitDecodes() {
        // Each component is two JSON levels (object + children array), MAX_DEPTH counts both.
        val screen = assertNeverThrows("40 components deep", """{"body":${nested(40)}}""")
        assertTrue(screen.body is AdvColumn)
    }

    @Test
    fun aTreeBeyondTheLimitIsRejectedWithAReason() {
        val screen = assertNeverThrows("60 components deep", """{"body":${nested(60)}}""")
        val body = screen.body as UnknownComponent
        assertTrue("error should name the cause, was ${body.error}", body.error!!.contains("deeper"))
    }

    @Test
    fun bracketsInsideStringsDoNotCountAsNesting() {
        val noise = "[".repeat(500) + "{".repeat(500)
        val screen = assertNeverThrows("brackets in text", """{"body":{"type":"text","text":"$noise \" ["}}""")
        assertTrue(screen.body is AdvText)
    }

    @Test
    fun theElementOverloadAppliesTheSameLimit() {
        val element = kotlinx.serialization.json.Json.parseToJsonElement("""{"body":${nested(60)}}""")
        val screen = advBlueprint.decodeScreen(element)
        assertTrue(screen.body is UnknownComponent)
    }

    @Test
    fun veryDeepTreeDoesNotCrashTheApp() {
        // A hostile or buggy server can send arbitrary depth. A StackOverflowError is not an
        // IllegalArgumentException, so it would sail past the catch in decodeScreen.
        assertNeverThrows("depth 5000", """{"body":${nested(5000)}}""")
    }

    @Test
    fun nonStringTypeValuesDegradeInsteadOfThrowing() {
        listOf("5", "true", "null", "{}", """["text"]""", "1.5").forEach { type ->
            val screen = assertNeverThrows("type=$type", """{"body":{"type":$type,"text":"x"}}""")
            assertTrue("type=$type should degrade, got ${screen.body}", screen.body is UnknownComponent)
        }
    }

    @Test
    fun emptyAndOddInputsComeBackAsScreens() {
        listOf("", "   ", "null", "[]", "5", "\"str\"", "{", "{}", "{\"body\":{}}", "\u0000", "{\"body\":").forEach {
            val screen = assertNeverThrows("input=${it.take(12)}", it)
            assertTrue(screen.body is UnknownComponent)
        }
    }

    @Test
    fun escapedTypeStringStillMatchesTheRegistry() {
        val screen = assertNeverThrows("escaped", """{"body":{"type":"text","text":"x"}}""")
        assertEquals(AdvText("x"), screen.body)
    }

    @Test
    fun duplicateKeysDoNotThrow() {
        assertNeverThrows("dup", """{"body":{"type":"text","text":"a","text":"b"}}""")
    }

    @Test
    fun hugeNumbersInAModifierDoNotThrow() {
        assertNeverThrows(
            "overflow",
            """{"body":{"type":"text","text":"x","modifier":{"font_size":99999999999,"padding":{"all":1e400}}}}""",
        )
    }

    @Test
    fun malformedModifierOnARegisteredComponentKeepsTheComponent() {
        // CLAUDE.md: a malformed modifier degrades to null on an UNKNOWN node. For a registered
        // component the style is optional decoration, so losing the whole text is a heavy penalty.
        val screen = assertNeverThrows("bad modifier", """{"body":{"type":"text","text":"keep me","modifier":{"padding":"oops"}}}""")
        val body = screen.body
        assertTrue("registered text should survive a bad modifier, got $body", body is AdvText)
        assertEquals("keep me", (body as AdvText).text)
    }

    @Test
    fun unknownTypeStringIsKeptVerbatim() {
        val screen = assertNeverThrows("unicode", """{"body":{"type":"hölogram 🚀"}}""")
        assertEquals("hölogram 🚀", (screen.body as UnknownComponent).type)
        assertNotNull((screen.body as UnknownComponent).raw["type"] as? JsonPrimitive)
    }
}
