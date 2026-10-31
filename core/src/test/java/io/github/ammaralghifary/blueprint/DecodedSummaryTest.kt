package io.github.ammaralghifary.blueprint

import io.github.ammaralghifary.blueprint.model.DecodeError
import io.github.ammaralghifary.blueprint.model.DecodeErrorKind
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private val summaryRegistry = sduiRegistry {
    component<HookText>("text") { _, _, _ -> }
    component<HookColumn>("column") { _, _, _ -> }
}

class DecodedSummaryTest {
    private class Call(val screen: SduiScreen, val errors: List<DecodeError>)

    private val calls = mutableListOf<Call>()
    private val perError = mutableListOf<DecodeError>()
    private val blueprint = Blueprint(
        summaryRegistry,
        onDecodeError = { perError += it },
        onDecoded = { screen, errors -> calls += Call(screen, errors) },
    )

    @Test
    fun aCleanPayloadFiresOnceWithAnEmptyList() {
        val screen = blueprint.decodeScreen("""{"screen_id":"s","body":{"type":"text","text":"a"}}""")
        val call = calls.single()
        assertSame(screen, call.screen)
        assertEquals("s", call.screen.id)
        assertTrue(call.errors.isEmpty())
    }

    @Test
    fun itFiresOncePerCallAndCarriesThatCallsErrorsInOrder() {
        blueprint.decodeScreen("""{"body":{"type":"column","children":[{"type":"hologram"},{"type":"text"}]}}""")
        blueprint.decodeScreen("""{"body":{"type":"text","text":"fine"}}""")
        assertEquals(2, calls.size)
        assertEquals(
            listOf(DecodeErrorKind.UnregisteredType, DecodeErrorKind.InvalidComponent),
            calls[0].errors.map { it.kind },
        )
        assertEquals(perError, calls[0].errors)
        assertTrue(calls[1].errors.isEmpty())
    }

    @Test
    fun theStringAndElementOverloadsEachFireExactlyOnce() {
        val json = """{"body":{"type":"hologram"}}"""
        blueprint.decodeScreen(json)
        assertEquals(1, calls.size)
        blueprint.decodeScreen(Json.parseToJsonElement(json))
        assertEquals(2, calls.size)
        assertEquals(1, calls[1].errors.size)
    }

    @Test
    fun anUnreadablePayloadStillFiresOnceAsInvalidPayload() {
        val screen = blueprint.decodeScreen("this is not json")
        val call = calls.single()
        assertSame(screen, call.screen)
        assertTrue(call.screen.body is UnknownComponent)
        assertEquals(listOf(DecodeErrorKind.InvalidPayload), call.errors.map { it.kind })
    }

    @Test
    fun aBadEnvelopeWithABadChildReportsEachProblemOnceDespiteTheRetry() {
        val screen = blueprint.decodeScreen(
            """{"screen_id":{"oops":1},"body":{"type":"column","children":[{"type":"text"}]}}""",
        )
        assertTrue(screen.body is HookColumn)
        val kinds = calls.single().errors.map { it.kind }
        assertEquals(1, kinds.count { it == DecodeErrorKind.InvalidComponent })
        assertEquals(1, kinds.count { it == DecodeErrorKind.InvalidPayload })
        assertEquals(perError, calls.single().errors)
    }

    @Test
    fun aThrowingSummaryCallbackIsSwallowedAndErrorsWereStillDelivered() {
        val b = Blueprint(
            summaryRegistry,
            onDecodeError = { perError += it },
            onDecoded = { _, _ -> throw IllegalStateException("host bug") },
        )
        val screen = b.decodeScreen("""{"body":{"type":"hologram"}}""")
        assertTrue(screen.body is UnknownComponent)
        assertEquals(1, perError.size)
    }

    @Test
    fun aThrowingErrorCallbackDoesNotStopTheSummary() {
        val b = Blueprint(
            summaryRegistry,
            onDecodeError = { throw IllegalStateException("host bug") },
            onDecoded = { screen, errors -> calls += Call(screen, errors) },
        )
        b.decodeScreen("""{"body":{"type":"hologram"}}""")
        assertEquals(1, calls.single().errors.size)
    }

    @Test
    fun aThrowingSummaryOnAnUnreadablePayloadStillReturnsAScreen() {
        val b = Blueprint(summaryRegistry, onDecoded = { _, _ -> throw IllegalStateException("host bug") })
        assertTrue(b.decodeScreen("[1,2").body is UnknownComponent)
    }

    @Test
    fun theSummaryIsOptional() {
        val screen = Blueprint(summaryRegistry).decodeScreen("""{"body":{"type":"hologram"}}""")
        assertTrue(screen.body is UnknownComponent)
    }

    @Test
    fun theErrorsHandedToTheCallbackAreASnapshot() {
        blueprint.decodeScreen("""{"body":{"type":"hologram"}}""")
        val first = calls.single().errors
        blueprint.decodeScreen("""{"body":{"type":"text"}}""")
        assertEquals(1, first.size)
        assertEquals(DecodeErrorKind.UnregisteredType, first.single().kind)
    }
}
