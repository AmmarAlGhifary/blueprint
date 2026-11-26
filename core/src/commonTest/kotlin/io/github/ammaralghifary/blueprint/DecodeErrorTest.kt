package io.github.ammaralghifary.blueprint

import io.github.ammaralghifary.blueprint.model.DecodeError
import io.github.ammaralghifary.blueprint.model.DecodeErrorKind
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable

@Serializable
data class HookText(val text: String, override val modifier: SduiModifier? = null) : SduiComponent

@Serializable
data class HookColumn(val children: List<SduiComponent> = emptyList(), override val modifier: SduiModifier? = null) : SduiComponent

@Serializable
data class HookAction(val destination: String) : SduiAction

@Serializable
data class HookTap(val action: SduiAction? = null, override val modifier: SduiModifier? = null) : SduiComponent

internal val hookRegistry = sduiRegistry {
    component<HookText>("text") { _, _, _ -> }
    component<HookColumn>("column") { _, _, _ -> }
    component<HookTap>("tap") { _, _, _ -> }
    action<HookAction>("go")
}

/** @author Ammar Al Ghifary */
class DecodeErrorTest {
    private val seen = mutableListOf<DecodeError>()
    private val blueprint = Blueprint(hookRegistry, onDecodeError = { seen += it })

    private fun kinds() = seen.map { it.kind }

    @Test
    fun aCleanPayloadReportsNothing() {
        blueprint.decodeScreen("""{"screen_id":"s","body":{"type":"column","children":[{"type":"text","text":"a"}]}}""")
        assertTrue(seen.isEmpty())
    }

    @Test
    fun anUnregisteredTypeIsReportedOnceWithItsTypeAndRaw() {
        blueprint.decodeScreen("""{"body":{"type":"column","children":[{"type":"hologram","x":1}]}}""")
        assertEquals(listOf(DecodeErrorKind.UnregisteredType), kinds())
        assertEquals("hologram", seen[0].type)
        assertNotNull(seen[0].raw)
    }

    @Test
    fun aRegisteredTypeWithBadFieldsIsReportedAsInvalidComponent() {
        blueprint.decodeScreen("""{"body":{"type":"text"}}""")
        assertEquals(listOf(DecodeErrorKind.InvalidComponent), kinds())
        assertEquals("text", seen[0].type)
        assertNotNull(seen[0].message)
    }

    @Test
    fun aNodeWithoutATypeIsReportedWithANullType() {
        blueprint.decodeScreen("""{"body":{"text":"no type"}}""")
        assertEquals(listOf(DecodeErrorKind.UnregisteredType), kinds())
        assertEquals(null, seen[0].type)
    }

    @Test
    fun theReportedMessageIsTheUnknownComponentError() {
        // Hosts that log both must see one string, and the raw node must come along for debugging.
        val screen = blueprint.decodeScreen("""{"body":{"type":"text"}}""")
        assertEquals((screen.body as UnknownComponent).error, seen.single().message)
        assertEquals("text", (seen.single().raw!!["type"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun everyBadSiblingIsReportedOnceInOrder() {
        blueprint.decodeScreen(
            """{"body":{"type":"column","children":[{"type":"nope"},{"type":"text"},{"type":"text","text":"ok"}]}}""",
        )
        assertEquals(listOf("nope", "text"), seen.map { it.type })
    }

    @Test
    fun aBadActionIsReportedAsInvalidAction() {
        blueprint.decodeScreen("""{"body":{"type":"tap","action":{"type":"go"}}}""")
        assertTrue(DecodeErrorKind.InvalidAction in kinds())
    }

    @Test
    fun anUnregisteredActionIsReported() {
        blueprint.decodeScreen("""{"body":{"type":"tap","action":{"type":"teleport"}}}""")
        assertEquals(listOf(DecodeErrorKind.UnregisteredType), kinds())
        assertEquals("teleport", seen[0].type)
    }

    @Test
    fun aMalformedModifierKeepsTheComponentAndIsReportedAsIgnored() {
        val screen = blueprint.decodeScreen("""{"body":{"type":"text","text":"keep","modifier":{"padding":"oops"}}}""")
        assertEquals(HookText("keep"), screen.body)
        assertEquals(listOf(DecodeErrorKind.ModifierIgnored), kinds())
    }

    @Test
    fun aBadModifierAlongsideAMissingFieldIsStillAnInvalidComponent() {
        val screen = blueprint.decodeScreen("""{"body":{"type":"text","modifier":{"padding":"oops"}}}""")
        assertTrue(screen.body is UnknownComponent)
        assertEquals(listOf(DecodeErrorKind.InvalidComponent), kinds())
    }

    @Test
    fun aRetriedNodeDoesNotReportItsChildrenTwice() {
        // The column's bad modifier forces a retry; the unregistered child must still be reported once.
        blueprint.decodeScreen(
            """{"body":{"type":"column","modifier":{"padding":"oops"},"children":[{"type":"hologram"}]}}""",
        )
        assertEquals(1, kinds().count { it == DecodeErrorKind.UnregisteredType })
        assertEquals(1, kinds().count { it == DecodeErrorKind.ModifierIgnored })
        assertEquals(2, seen.size)
    }

    @Test
    fun salvagingTheEnvelopeDoesNotReportTheBodyTwice() {
        // version is the wrong type, so the screen decode fails and the body is decoded again on its own.
        val screen = blueprint.decodeScreen(
            """{"screen_id":"s","version":"x","body":{"type":"column","children":[{"type":"hologram"}]}}""",
        )
        assertEquals("s", screen.id)
        assertEquals(1, kinds().count { it == DecodeErrorKind.UnregisteredType })
        assertEquals(1, kinds().count { it == DecodeErrorKind.InvalidPayload })
    }

    @Test
    fun aMissingBodyKeepsTheMetadataAndIsReported() {
        val screen = blueprint.decodeScreen("""{"screen_id":"s","version":3}""")
        assertEquals("s", screen.id)
        assertEquals(3, screen.version)
        assertEquals(listOf(DecodeErrorKind.InvalidPayload), kinds())
    }

    @Test
    fun malformedJsonIsReportedOnce() {
        blueprint.decodeScreen("{")
        assertEquals(listOf(DecodeErrorKind.InvalidPayload), kinds())
    }

    @Test
    fun anOverlyDeepPayloadIsReportedOnce() {
        val deep = "[".repeat(Blueprint.MAX_DEPTH + 5) + "]".repeat(Blueprint.MAX_DEPTH + 5)
        blueprint.decodeScreen(deep)
        assertEquals(listOf(DecodeErrorKind.InvalidPayload), kinds())
        assertTrue(seen[0].message!!.contains("deeper"))
    }

    @Test
    fun theElementOverloadReportsToo() {
        blueprint.decodeScreen(kotlinx.serialization.json.Json.parseToJsonElement("""{"body":{"type":"nope"}}"""))
        assertEquals(listOf(DecodeErrorKind.UnregisteredType), kinds())
    }

    @Test
    fun decodingDirectlyThroughJsonNeitherReportsNorThrows() {
        val component = blueprint.json.decodeFromString(PolymorphicSerializer(SduiComponent::class), """{"type":"hologram"}""")
        assertTrue(component is UnknownComponent)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun aThrowingCallbackDoesNotBreakDecodingOrLaterReports() {
        var delivered = 0
        val shaky = Blueprint(hookRegistry, onDecodeError = {
            delivered++
            error("observer bug")
        })
        val screen = shaky.decodeScreen("""{"body":{"type":"column","children":[{"type":"a"},{"type":"b"}]}}""")
        assertTrue(screen.body is HookColumn)
        assertEquals(2, delivered)
    }

    @Test
    fun aCallbackThatDecodesAgainDoesNotCorruptTheOuterReports() {
        var reentered = false
        lateinit var bp: Blueprint
        bp = Blueprint(hookRegistry, onDecodeError = {
            seen += it
            if (!reentered) {
                reentered = true
                bp.decodeScreen("""{"body":{"type":"inner-unknown"}}""")
            }
        })
        bp.decodeScreen("""{"body":{"type":"column","children":[{"type":"a"},{"type":"b"}]}}""")
        // 2 outer + 1 from the nested decode inside the first callback.
        assertEquals(3, seen.size)
        assertEquals(setOf("a", "b", "inner-unknown"), seen.map { it.type }.toSet())
    }
}
