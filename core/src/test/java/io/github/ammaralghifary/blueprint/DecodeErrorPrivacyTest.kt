package io.github.ammaralghifary.blueprint

import io.github.ammaralghifary.blueprint.model.DecodeError
import io.github.ammaralghifary.blueprint.model.DecodeErrorKind
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.model.UnknownAction
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Serializable
data class PvText(val text: String, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class PvColumn(val children: List<SduiComponent> = emptyList(), override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class PvGo(val destination: String, val params: Map<String, String> = emptyMap()) : SduiAction()

@Serializable
data class PvTap(val action: SduiAction? = null, override val modifier: SduiModifier? = null) : SduiComponent()

// A host model with its own validation: its message may carry anything, so it must never be echoed.
@Serializable
data class PvStrict(val n: Int, override val modifier: SduiModifier? = null) : SduiComponent() {
    init {
        require(n > 0) { "n was $n, password=hunter2" }
    }
}

private const val SECRET = "hunter2"

// DecodeError.message, its toString and UnknownComponent.error are meant to be loggable, so a
// payload value must never appear in them, wherever in the payload it sits.
/** @author Ammar Al Ghifary */
class DecodeErrorPrivacyTest {
    private val seen = mutableListOf<DecodeError>()
    private val blueprint = Blueprint(
        sduiRegistry {
            component<PvText>("text") { _, _, _ -> }
            component<PvColumn>("column") { _, _, _ -> }
            component<PvTap>("tap") { _, _, _ -> }
            component<PvStrict>("strict") { _, _, _ -> }
            action<PvGo>("go")
        },
        onDecodeError = { seen += it },
    )

    /** Every `error` string a degraded component or action carries anywhere in the tree. */
    private fun treeErrors(c: SduiComponent): List<String> = when (c) {
        is UnknownComponent -> listOfNotNull(c.error)
        is PvColumn -> c.children.flatMap(::treeErrors)
        is PvTap -> listOfNotNull((c.action as? UnknownAction)?.error)
        else -> emptyList()
    }

    private fun decode(payload: String) = blueprint.decodeScreen(payload)

    @Test
    fun aSecretNeverReachesAMessageOrToStringWhereverItSits() {
        val cases = mapOf(
            "object where a string is expected" to """{"body":{"type":"text","text":{"x":"$SECRET"}}}""",
            "string where an object is expected" to """{"body":{"type":"text","text":"ok","modifier":{"padding":"$SECRET"}}}""",
            "string where an int is expected" to """{"body":{"type":"text","text":"ok","modifier":{"font_size":"$SECRET"}}}""",
            "string where a nested int is expected" to """{"body":{"type":"text","text":"ok","modifier":{"padding":{"all":"$SECRET"}}}}""",
            "string where a float is expected" to """{"body":{"type":"text","text":"ok","modifier":{"weight":"$SECRET"}}}""",
            "an int that overflows" to """{"body":{"type":"text","text":"ok","modifier":{"font_size":99999999999}}}""",
            "a secret beside a missing field" to """{"body":{"type":"text","note":"$SECRET"}}""",
            "action field with the wrong shape" to """{"body":{"type":"tap","action":{"type":"go","destination":{"s":"$SECRET"}}}}""",
            "a secret used as a map key" to """{"body":{"type":"tap","action":{"type":"go","destination":"d","params":{"$SECRET":{"a":1}}}}}""",
            "unterminated json" to """{"body":{"type":"text","text":"$SECRET""",
            "a bare word instead of an object" to """{"body": $SECRET}""",
            "bad screen metadata" to """{"screen_id":{"a":"$SECRET"},"body":{"type":"text","text":"x"}}""",
            "a secret as the unregistered type" to """{"body":{"type":"$SECRET"}}""",
            "a host model whose own check fails" to """{"body":{"type":"strict","n":-1}}""",
        )
        for ((label, payload) in cases) {
            seen.clear()
            val screen = decode(payload)
            assertTrue("[$label] should have been reported at all, or this test proves nothing", seen.isNotEmpty())
            seen.forEach {
                assertFalse("[$label] message leaked: ${it.message}", it.message.orEmpty().contains(SECRET))
                assertFalse("[$label] toString leaked: $it", it.toString().contains(SECRET))
            }
            treeErrors(screen.body).forEach { assertFalse("[$label] error leaked: $it", it.contains(SECRET)) }
        }
    }

    @Test
    fun theMessageStillNamesTheFieldAndTheExpectedKind() {
        decode("""{"body":{"type":"text","text":"ok","modifier":{"font_size":"$SECRET"}}}""")
        assertEquals("Expected an int value at \$.font_size", seen.single().message)

        seen.clear()
        decode("""{"body":{"type":"text","text":{"x":1}}}""")
        assertEquals("Expected JsonPrimitive, but had JsonObject for string at \$.text", seen.single().message)

        seen.clear()
        decode("""{"body":{"type":"text"}}""")
        assertEquals("Missing required field 'text'", seen.single().message)
    }

    @Test
    fun aMalformedPayloadSaysWhereAndWhatWasExpectedButNotWhatItSaid() {
        decode("""{"body":{"type":"text","text":"$SECRET""")
        val message = seen.single().message!!
        assertTrue(message, message.startsWith("Malformed JSON at offset "))
        assertFalse(message, message.contains("JSON input"))
    }

    @Test
    fun aPathKeepsSchemaFieldNamesButMasksPayloadMapKeys() {
        decode("""{"body":{"type":"tap","action":{"type":"go","destination":{"s":1}}}}""")
        assertTrue(seen.single().message!!.endsWith("at \$.destination"))

        seen.clear()
        decode("""{"body":{"type":"tap","action":{"type":"go","destination":"d","params":{"$SECRET":{"a":1}}}}}""")
        assertTrue(seen.single().message!!.endsWith("at \$.*"))
    }

    @Test
    fun aHostModelsOwnMessageIsReducedToItsExceptionName() {
        decode("""{"body":{"type":"strict","n":-1}}""")
        assertEquals("Invalid value (IllegalArgumentException)", seen.single().message)
    }

    @Test
    fun anUnregisteredTypeIsOnlyOnTheTypePropertyNeverInTheMessageOrToString() {
        decode("""{"body":{"type":"$SECRET"}}""")
        val error = seen.single()
        assertEquals(SECRET, error.type)
        assertFalse(error.message.orEmpty().contains(SECRET))
        assertEquals("DecodeError(kind=UnregisteredType, message=No component registered for this type)", error.toString())
    }

    @Test
    fun toStringShowsARegisteredTypeButNeverTheRawNode() {
        decode("""{"body":{"type":"text","text":{"x":"$SECRET"}}}""")
        val error = seen.single()
        assertEquals(
            "DecodeError(kind=InvalidComponent, type=text, message=Expected JsonPrimitive, but had JsonObject for string at \$.text)",
            error.toString(),
        )
        // raw is the node itself, so it does hold the secret: that is why the KDoc says to keep it out of analytics.
        assertTrue(error.raw.toString().contains(SECRET))
    }

    @Test
    fun theUnknownComponentErrorIsTheSameSafeString() {
        val screen = decode("""{"body":{"type":"text","text":{"x":"$SECRET"}}}""")
        assertEquals((screen.body as UnknownComponent).error, seen.single().message)
    }

    @Test
    fun errorsFromTheSamePayloadAreEqualAndHashTheSame() {
        decode("""{"body":{"type":"text"}}""")
        decode("""{"body":{"type":"text"}}""")
        val (a, b) = seen
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(1, setOf(a, b).size)
    }

    @Test
    fun errorsDifferInKindTypeMessageOrRawAreNotEqual() {
        decode("""{"body":{"type":"text"}}""")
        decode("""{"body":{"type":"nope"}}""")
        decode("""{"body":{"type":"text","text":{"x":1}}}""")
        decode("""{"body":{"type":"text","note":"a"}}""")
        val (missing, unregistered, wrongShape, withNote) = seen
        assertNotEquals(missing, unregistered)
        assertNotEquals(missing, wrongShape)
        assertEquals(DecodeErrorKind.InvalidComponent, withNote.kind)
        // same kind, type and message as `missing`, but a different raw node
        assertEquals(missing.message, withNote.message)
        assertNotEquals(missing, withNote)
    }
}
