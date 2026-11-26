package io.github.ammaralghifary.blueprint

import io.github.ammaralghifary.blueprint.model.DecodeError
import io.github.ammaralghifary.blueprint.model.DecodeErrorKind
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private const val BODY = """"body":{"type":"text","text":"hi"}"""

/** @author Ammar Al Ghifary */
class SchemaVersionTest {
    private val seen = mutableListOf<DecodeError>()
    private val blueprint = Blueprint(hookRegistry, onDecodeError = { seen += it })

    private fun decode(payload: String): SduiScreen {
        seen.clear()
        return blueprint.decodeScreen(payload)
    }

    private fun withVersion(value: String) = decode("""{"schema_version":$value,$BODY}""")

    private fun kinds() = seen.map { it.kind }

    @Test
    fun theSupportedRangeIsMajorOne() {
        assertEquals(1..1, Blueprint.supportedSchemaVersions)
    }

    @Test
    fun aMissingSchemaVersionIsAcceptedAndStaysNull() {
        val screen = decode("""{$BODY}""")
        assertNull(screen.schemaVersion)
        assertEquals(HookText("hi"), screen.body)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun anExplicitNullIsTreatedAsMissing() {
        val screen = withVersion("null")
        assertNull(screen.schemaVersion)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun aSupportedVersionIsKeptAndNotReportedEvenWhenQuoted() {
        for (value in listOf("1", "\"1\"")) {
            val screen = withVersion(value)
            assertEquals(1, screen.schemaVersion, value)
            assertEquals(HookText("hi"), screen.body, value)
            assertTrue(seen.isEmpty(), value)
        }
    }

    @Test
    fun anOlderVersionStillDecodesAndIsReportedOnce() {
        for (value in listOf(0, -1, Int.MIN_VALUE)) {
            val screen = withVersion("$value")
            assertEquals(value, screen.schemaVersion)
            assertEquals(HookText("hi"), screen.body)
            assertEquals(listOf(DecodeErrorKind.UnsupportedVersion), kinds())
            assertEquals("Unsupported schema_version: older than the supported range 1..1", seen.single().message)
        }
    }

    @Test
    fun aNewerVersionStillDecodesAndIsReportedOnce() {
        for (value in listOf("2", "\"2\"", "${Int.MAX_VALUE}")) {
            val screen = withVersion(value)
            assertEquals(HookText("hi"), screen.body, value)
            assertEquals(listOf(DecodeErrorKind.UnsupportedVersion), kinds(), value)
            assertEquals("Unsupported schema_version: newer than the supported range 1..1", seen.single().message)
        }
    }

    @Test
    fun theReportHasNoTypeAndCarriesTheWholePayload() {
        val payload = """{"schema_version":2,$BODY}"""
        decode(payload)
        assertNull(seen.single().type)
        assertEquals(Json.parseToJsonElement(payload).jsonObject, seen.single().raw)
    }

    @Test
    fun anUnreadableVersionIsABadEnvelopeFieldAndKeepsTheRest() {
        for (value in listOf("\"two\"", "\"\"", "1.5", "2.0", "true", "{}", "[2]", "99999999999")) {
            val screen = decode("""{"screen_id":"s","version":7,"schema_version":$value,$BODY}""")
            assertNull(screen.schemaVersion, value)
            assertEquals("s", screen.id, value)
            assertEquals(7, screen.version, value)
            assertEquals(HookText("hi"), screen.body, value)
            assertEquals(listOf(DecodeErrorKind.InvalidPayload), kinds(), value)
            assertTrue(seen.single().message!!.startsWith("Ignored invalid screen metadata: "), seen.single().message)
        }
    }

    @Test
    fun theHostsOwnVersionFieldIsNeverActedOn() {
        val screen = decode("""{"version":99,$BODY}""")
        assertEquals(99, screen.version)
        assertNull(screen.schemaVersion)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun aBadScreenIdAndAnUnsupportedVersionAreOneErrorEachWithNoDuplicates() {
        val screen = decode(
            """{"screen_id":{"a":1},"schema_version":2,"body":{"type":"column","children":[{"type":"hologram"}]}}""",
        )
        assertEquals(2, screen.schemaVersion)
        assertTrue(screen.body is HookColumn)
        assertEquals(1, kinds().count { it == DecodeErrorKind.UnsupportedVersion })
        assertEquals(1, kinds().count { it == DecodeErrorKind.InvalidPayload })
        assertEquals(1, kinds().count { it == DecodeErrorKind.UnregisteredType })
        assertEquals(3, seen.size)
    }

    @Test
    fun aBadScreenIdAloneGivesExactlyTwoErrorsWithAnUnsupportedVersion() {
        decode("""{"screen_id":{"a":1},"schema_version":2,$BODY}""")
        assertEquals(listOf(DecodeErrorKind.UnsupportedVersion, DecodeErrorKind.InvalidPayload), kinds())
    }

    @Test
    fun aMissingBodyStillReportsTheUnsupportedVersionAndKeepsIt() {
        // A newer major may well have moved the body, so this is when the version matters most.
        val screen = decode("""{"schema_version":2}""")
        assertTrue(screen.body is UnknownComponent)
        assertEquals(2, screen.schemaVersion)
        assertEquals(listOf(DecodeErrorKind.UnsupportedVersion, DecodeErrorKind.InvalidPayload), kinds())
    }

    @Test
    fun anOverlyDeepPayloadStillReportsTheUnsupportedVersion() {
        val levels = Blueprint.MAX_DEPTH + 5
        val deep = "[".repeat(levels) + "]".repeat(levels)
        seen.clear()
        blueprint.decodeScreen(Json.parseToJsonElement("""{"schema_version":2,"body":$deep}"""))
        assertEquals(listOf(DecodeErrorKind.UnsupportedVersion, DecodeErrorKind.InvalidPayload), kinds())
    }

    @Test
    fun theElementOverloadReportsItToo() {
        blueprint.decodeScreen(Json.parseToJsonElement("""{"schema_version":2,$BODY}"""))
        assertEquals(listOf(DecodeErrorKind.UnsupportedVersion), kinds())
    }
}
