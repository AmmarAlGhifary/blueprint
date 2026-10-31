package io.github.ammaralghifary.blueprint

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Serializable
data class Label(val text: String, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class Stack(val children: List<SduiComponent>, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class Bar(val children: List<SduiComponent>, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class Counter(val label: String, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class Tap(
    val text: String,
    val action: SduiAction? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

@Serializable
data class Go(val destination: String) : SduiAction()

/**
 * Renders through a real Compose runtime (Robolectric, JVM). Decoding tests cannot catch render-time
 * failures such as invoking a registered composable lambda (the ComposableLambdaImpl arity cast crash).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlueprintRendererTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun markDebuggable() {
        // The default placeholder is only drawn in debuggable apps.
        val info = ApplicationProvider.getApplicationContext<Application>().applicationInfo
        info.flags = info.flags or ApplicationInfo.FLAG_DEBUGGABLE
    }

    private val blueprint = Blueprint(
        sduiRegistry {
            component<Label>("label") { c, mod, _ -> BasicText(c.text, mod) }
            component<Stack>("stack") { c, mod, scope ->
                Column(mod) { scope.RenderAll(c.children) }
            }
            component<Bar>("bar") { c, mod, scope ->
                Row(mod) {
                    // The scope-only half of the server modifier: only this Row can resolve weight.
                    scope.RenderAll(c.children) { child ->
                        child.modifier?.weight?.let { Modifier.weight(it) } ?: Modifier
                    }
                }
            }
            component<Counter>("counter") { c, mod, _ ->
                var taps by remember { mutableStateOf(0) }
                BasicText("${c.label} $taps", mod.clickable { taps++ })
            }
            component<Tap>("tap") { c, mod, scope ->
                BasicText(c.text, mod.clickable { c.action?.let(scope::dispatch) })
            }
            action<Go>("go")
        },
    )

    private fun show(json: String, onAction: (SduiAction) -> Unit = {}, bp: Blueprint = blueprint) {
        val screen = bp.decodeScreen(json)
        compose.setContent { BlueprintRenderer(screen, bp, onAction = onAction) }
    }

    @Test
    fun rendersNestedTreeAndPlaceholderForUnregisteredSibling() {
        show(
            """{"body":{"type":"stack","children":[
                {"type":"label","text":"first"},
                {"type":"hologram"},
                {"type":"label","text":"last"}]}}""",
        )
        compose.onNodeWithText("first").assertIsDisplayed()
        compose.onNodeWithText("Unknown component: hologram").assertIsDisplayed()
        compose.onNodeWithText("last").assertIsDisplayed()
    }

    @Test
    fun registeredComponentWithBadFieldsShowsPlaceholderAndSiblingsSurvive() {
        show(
            """{"body":{"type":"stack","children":[
                {"type":"label"},
                {"type":"label","text":"still here"}]}}""",
        )
        compose.onNodeWithText("Unknown component: label", substring = true).assertIsDisplayed()
        compose.onNodeWithText("still here").assertIsDisplayed()
    }

    @Test
    fun serverModifierIsAppliedBeforeTheComponentRuns() {
        show("""{"body":{"type":"label","text":"sized","modifier":{"width":{"type":"exact","value":120}}}}""")
        compose.onNodeWithText("sized").assertWidthIsEqualTo(120.dp)
    }

    @Test
    fun dispatchesDecodedActionsToOnAction() {
        val received = mutableListOf<SduiAction>()
        show(
            """{"body":{"type":"stack","children":[
                {"type":"tap","text":"go home","action":{"type":"go","destination":"home"}},
                {"type":"tap","text":"teleport","action":{"type":"teleport"}}]}}""",
            onAction = { received += it },
        )
        compose.onNodeWithText("go home").performClick()
        compose.onNodeWithText("teleport").performClick()
        assertEquals(Go("home"), received[0])
        assertTrue(received[1] is UnknownAction)
    }

    @Test
    fun customFallbackReplacesTheDefaultPlaceholder() {
        val custom = Blueprint(
            sduiRegistry {
                component<Label>("label") { c, mod, _ -> BasicText(c.text, mod) }
                fallback { component, _ -> BasicText("custom:${component.type}") }
            },
        )
        show("""{"body":{"type":"mystery"}}""", bp = custom)
        compose.onNodeWithText("custom:mystery").assertIsDisplayed()
    }

    @Test
    fun renderAllRendersEveryChild() {
        show(
            """{"body":{"type":"stack","children":[
                {"type":"label","text":"a"},{"type":"label","text":"b"},{"type":"label","text":"c"}]}}""",
        )
        compose.onNodeWithText("a").assertIsDisplayed()
        compose.onNodeWithText("b").assertIsDisplayed()
        compose.onNodeWithText("c").assertIsDisplayed()
    }

    @Test
    fun containerAppliesServerWeightHandedDownThroughRenderAll() {
        show(
            """{"body":{"type":"bar","modifier":{"width":{"type":"exact","value":300}},"children":[
                {"type":"label","text":"one third","modifier":{"weight":1}},
                {"type":"label","text":"two thirds","modifier":{"weight":2}}]}}""",
        )
        compose.onNodeWithText("one third").assertWidthIsEqualTo(100.dp)
        compose.onNodeWithText("two thirds").assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun childStateSurvivesARefetchThatKeepsTheTreeShape() {
        var screen by mutableStateOf(blueprint.decodeScreen(counterPayload("first")))
        compose.setContent { BlueprintRenderer(screen, blueprint) }

        compose.onNodeWithText("first 0").performClick()
        compose.onNodeWithText("first 1").assertIsDisplayed()

        // Same shape, new data: the counter keeps its tap count because RenderAll keys it.
        compose.runOnIdle { screen = blueprint.decodeScreen(counterPayload("second")) }
        compose.onNodeWithText("second 1").assertIsDisplayed()
    }

    @Test
    fun unreadablePayloadRendersAPlaceholderInsteadOfCrashing() {
        show("this is not json")
        compose.onNodeWithText("Unknown component: <no type>", substring = true).assertIsDisplayed()
    }

    private fun counterPayload(label: String): String =
        """{"body":{"type":"stack","children":[{"type":"counter","label":"$label"}]}}"""
}
