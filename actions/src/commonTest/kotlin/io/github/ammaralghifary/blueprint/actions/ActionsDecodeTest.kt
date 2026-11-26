package io.github.ammaralghifary.blueprint.actions

import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.DecodeError
import io.github.ammaralghifary.blueprint.model.DecodeErrorKind
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.model.UnknownAction
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Trigger(val action: SduiAction, override val modifier: SduiModifier? = null) : SduiComponent

private const val SECRET = "hunter2"

/** @author Ammar Al Ghifary */
class ActionsDecodeTest {
    private val errors = mutableListOf<DecodeError>()
    private val blueprint = Blueprint(
        sduiRegistry {
            component<Trigger>("trigger") { _, _, _ -> }
            sduiActions()
        },
        onDecodeError = { errors += it },
    )

    private fun action(json: String): SduiAction =
        (blueprint.decodeScreen("""{"body":{"type":"trigger","action":$json}}""").body as Trigger).action

    /** The payload degrades to an [UnknownAction] that is reported once, and its component survives. */
    private fun assertDegrades(json: String, type: String) {
        errors.clear()
        val unknown = action(json) as UnknownAction
        assertEquals(type, unknown.type)
        assertNotNull(unknown.error)
        assertEquals(listOf(DecodeErrorKind.InvalidAction), errors.map { it.kind })
    }

    @Test
    fun openUrlDecodes() {
        assertEquals(OpenUrlAction("https://example.com"), action("""{"type":"open_url","url":"https://example.com"}"""))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun openUrlWithoutAUrlDegrades() {
        assertDegrades("""{"type":"open_url"}""", "open_url")
        assertDegrades("""{"type":"open_url","url":{"a":1}}""", "open_url")
    }

    @Test
    fun submitDecodesWithAndWithoutParams() {
        assertEquals(
            SubmitAction("signup", mapOf("plan" to "free")),
            action("""{"type":"submit","target":"signup","params":{"plan":"free"}}"""),
        )
        assertEquals(SubmitAction("signup"), action("""{"type":"submit","target":"signup"}"""))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun submitWithoutATargetOrWithBadParamsDegrades() {
        assertDegrades("""{"type":"submit"}""", "submit")
        assertDegrades("""{"type":"submit","target":"t","params":[1]}""", "submit")
    }

    @Test
    fun setStateKeepsTheJsonTypeOfItsValue() {
        assertEquals(SetStateAction("k", JsonPrimitive("a")), action("""{"type":"set_state","key":"k","value":"a"}"""))
        assertEquals(SetStateAction("k", JsonPrimitive(3)), action("""{"type":"set_state","key":"k","value":3}"""))
        assertEquals(SetStateAction("k", JsonPrimitive(true)), action("""{"type":"set_state","key":"k","value":true}"""))
        // The string "3" is not the number 3.
        assertNotEquals(action("""{"type":"set_state","key":"k","value":3}"""), action("""{"type":"set_state","key":"k","value":"3"}"""))
        assertNull((action("""{"type":"set_state","key":"k","value":null}""") as SetStateAction).value)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun setStateWithoutAKeyOrValueOrWithAStructuredValueDegrades() {
        assertDegrades("""{"type":"set_state","value":1}""", "set_state")
        // A missing value is a mistake, not a request to remove the key.
        assertDegrades("""{"type":"set_state","key":"k"}""", "set_state")
        assertDegrades("""{"type":"set_state","key":"k","value":{"a":1}}""", "set_state")
        assertDegrades("""{"type":"set_state","key":"k","value":[1]}""", "set_state")
    }

    @Test
    fun aTypeAlreadyTakenThrows() {
        assertFailsWith<IllegalArgumentException> {
            sduiRegistry {
                sduiActions()
                sduiActions()
            }
        }
    }

    @Test
    fun noToStringCarriesPayloadText() {
        val actions = listOf(
            OpenUrlAction("https://example.com/?token=$SECRET"),
            SubmitAction(SECRET, mapOf(SECRET to SECRET)),
            SetStateAction(SECRET, JsonPrimitive(SECRET)),
        )
        for (it in actions) {
            assertFalse(it.toString().contains(SECRET), "leaked: $it")
            val event = ActionEvent(it, Trigger(it)).toString()
            assertFalse(event.contains(SECRET), "leaked: $event")
        }
        assertEquals("SubmitAction(params=1)", actions[1].toString())
    }

    @Test
    fun aDegradedActionDoesNotPutPayloadTextInItsError() {
        val unknown = action("""{"type":"set_state","key":"$SECRET","value":{"$SECRET":"$SECRET"}}""") as UnknownAction
        assertFalse(unknown.error.orEmpty().contains(SECRET), "leaked: ${unknown.error}")
        assertFalse(errors.single().toString().contains(SECRET), "leaked: ${errors.single()}")
    }
}
