package io.github.ammaralghifary.blueprint.defaults

import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.DecodeError
import io.github.ammaralghifary.blueprint.model.DecodeErrorKind
import io.github.ammaralghifary.blueprint.model.NavigateAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.UnknownAction
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @author Ammar Al Ghifary */
class DefaultsDialogDecodeTest {
    private val errors = mutableListOf<DecodeError>()
    private val blueprint = Blueprint(DefaultSduiRegistry, onDecodeError = { errors += it })

    private fun body(json: String): SduiComponent = blueprint.decodeScreen("""{"body":$json}""").body

    private fun dialog(json: String): SduiDialog = body(json) as SduiDialog

    private fun assertInvalid(json: String) {
        errors.clear()
        val node = body(json)
        assertTrue(node is UnknownComponent, "expected UnknownComponent but was $node")
        assertEquals("dialog", node.type)
        assertNotNull(node.error)
        assertEquals(listOf(DecodeErrorKind.InvalidComponent), errors.map { it.kind })
    }

    @Test
    fun aFullDialogDecodes() {
        val decoded = dialog(
            """{"type":"dialog","title":"No connection","message":"Check your network and try again.",
                "primary":{"label":"Retry","action":{"type":"navigate","destination":"reload"},"disabled_seconds":5},
                "secondary":{"label":"Close"},
                "dismissible":false,"on_dismiss":{"type":"navigate","destination":"back"}}""",
        )
        assertEquals(
            SduiDialog(
                title = "No connection",
                message = "Check your network and try again.",
                primary = SduiDialogButton("Retry", NavigateAction("reload"), disabledSeconds = 5),
                secondary = SduiDialogButton("Close"),
                dismissible = false,
                onDismiss = NavigateAction("back"),
            ),
            decoded,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun aMinimalDialogHasTheDefaults() {
        val decoded = dialog("""{"type":"dialog","primary":{"label":"OK"}}""")
        assertEquals(SduiDialog(primary = SduiDialogButton("OK")), decoded)
        assertNull(decoded.title)
        assertNull(decoded.message)
        assertNull(decoded.secondary)
        assertNull(decoded.onDismiss)
        assertNull(decoded.primary.action)
        assertNull(decoded.primary.disabledSeconds)
        assertTrue(decoded.dismissible)
    }

    @Test
    fun aDialogWithoutAPrimaryButtonDegrades() {
        assertInvalid("""{"type":"dialog","title":"t"}""")
        assertInvalid("""{"type":"dialog","primary":null}""")
    }

    @Test
    fun aBadButtonDegradesTheDialog() {
        assertInvalid("""{"type":"dialog","primary":{}}""")
        assertInvalid("""{"type":"dialog","primary":"OK"}""")
        assertInvalid("""{"type":"dialog","primary":{"label":"OK"},"secondary":{"action":{"type":"navigate","destination":"d"}}}""")
        assertInvalid("""{"type":"dialog","primary":{"label":"OK","action":"reload"}}""")
    }

    @Test
    fun aBadDialogDoesNotTakeDownItsSiblings() {
        val column = body("""{"type":"column","children":[{"type":"dialog"},{"type":"text","text":"alive"}]}""") as SduiColumn
        assertTrue(column.children[0] is UnknownComponent)
        assertEquals(SduiText("alive"), column.children[1])
    }

    @Test
    fun anUnknownNestedActionIsKeptAsUnknown() {
        val decoded = dialog(
            """{"type":"dialog","primary":{"label":"Go","action":{"type":"teleport"}},
                "on_dismiss":{"type":"vanish"}}""",
        )
        assertEquals("teleport", (decoded.primary.action as UnknownAction).type)
        assertEquals("vanish", (decoded.onDismiss as UnknownAction).type)
        assertEquals(List(2) { DecodeErrorKind.UnregisteredType }, errors.map { it.kind })
    }

    @Test
    fun aKnownNestedActionMissingAFieldIsKeptAsUnknown() {
        val decoded = dialog("""{"type":"dialog","primary":{"label":"Go","action":{"type":"navigate"}}}""")
        assertNotNull((decoded.primary.action as UnknownAction).error)
        assertEquals(listOf(DecodeErrorKind.InvalidAction), errors.map { it.kind })
    }

    @Test
    fun disabledSecondsIsKeptAsSentWhateverItsSize() {
        // The renderer decides what out-of-range values mean.
        for (seconds in listOf(0, -1, 1, 600, 601, Int.MAX_VALUE, Int.MIN_VALUE)) {
            val decoded = dialog("""{"type":"dialog","primary":{"label":"OK","disabled_seconds":$seconds}}""")
            assertEquals(seconds, decoded.primary.disabledSeconds)
        }
        assertNull(dialog("""{"type":"dialog","primary":{"label":"OK","disabled_seconds":null}}""").primary.disabledSeconds)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun disabledSecondsThatIsNotAnIntegerDegradesTheDialog() {
        for (bad in listOf("1.5", "\"soon\"", "99999999999", "[1]")) {
            assertInvalid("""{"type":"dialog","primary":{"label":"OK","disabled_seconds":$bad}}""")
        }
    }
}
