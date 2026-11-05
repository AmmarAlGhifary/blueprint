package io.github.ammaralghifary.blueprint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Serializable
data class EvTap(
    val text: String,
    val action: SduiAction? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

@Serializable
data class EvCard(
    val title: String,
    val action: SduiAction? = null,
    val children: List<SduiComponent> = emptyList(),
    override val modifier: SduiModifier? = null,
) : SduiComponent()

@Serializable
data class EvStack(val children: List<SduiComponent> = emptyList(), override val modifier: SduiModifier? = null) : SduiComponent()

/** @author Ammar Al Ghifary */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionEventTest {
    @get:Rule
    val compose = createComposeRule()

    private val blueprint = Blueprint(
        sduiRegistry {
            component<EvTap>("tap") { c, mod, scope ->
                BasicText(c.text, mod.clickable { c.action?.let(scope::dispatch) })
            }
            component<EvCard>("card") { c, mod, scope ->
                // Only the title is tappable: clickable merges its descendants, so a clickable Column
                // would swallow taps meant for the children.
                Column(mod) {
                    BasicText(c.title, Modifier.clickable { c.action?.let(scope::dispatch) })
                    scope.RenderAll(c.children)
                }
            }
            component<EvStack>("stack") { c, mod, scope -> Column(mod) { scope.RenderAll(c.children) } }
            action<Go>("go")
        },
    )

    private val events = mutableListOf<ActionEvent>()
    private val order = mutableListOf<String>()

    private fun show(json: String, withOnAction: Boolean = false) {
        val screen = blueprint.decodeScreen(json)
        compose.setContent {
            if (withOnAction) {
                BlueprintRenderer(
                    screen,
                    blueprint,
                    onActionEvent = { events += it; order += "event" },
                    onAction = { order += "action" },
                )
            } else {
                BlueprintRenderer(screen, blueprint, onActionEvent = { events += it })
            }
        }
    }

    @Test
    fun twoComponentsWithTheSameActionAreToldApart() {
        show(
            """{"body":{"type":"stack","children":[
                {"type":"tap","text":"first","action":{"type":"go","destination":"home"}},
                {"type":"tap","text":"second","action":{"type":"go","destination":"home"}}]}}""",
        )
        compose.onNodeWithText("second").performClick()
        compose.onNodeWithText("first").performClick()
        assertEquals(listOf("second", "first"), events.map { (it.source as EvTap).text })
        assertTrue(events.all { it.action == Go("home") })
    }

    @Test
    fun aNestedComponentReportsItselfNotItsContainer() {
        show(
            """{"body":{"type":"card","title":"card title","action":{"type":"go","destination":"card"},"children":[
                {"type":"tap","text":"inner","action":{"type":"go","destination":"inner"}}]}}""",
        )
        compose.onNodeWithText("inner").performClick()
        compose.onNodeWithText("card title").performClick()
        assertEquals(Go("inner"), events[0].action)
        assertTrue(events[0].source is EvTap)
        assertEquals(Go("card"), events[1].action)
        assertEquals("card title", (events[1].source as EvCard).title)
        assertEquals(2, events.size)
    }

    @Test
    fun bothCallbacksRunAndTheEventComesFirst() {
        show("""{"body":{"type":"tap","text":"go","action":{"type":"go","destination":"x"}}}""", withOnAction = true)
        compose.onNodeWithText("go").performClick()
        assertEquals(listOf("event", "action"), order)
    }

    @Test
    fun theSourceFollowsARefetchThatKeepsTheTreeShape() {
        var screen by mutableStateOf(
            blueprint.decodeScreen("""{"body":{"type":"tap","text":"old","action":{"type":"go","destination":"x"}}}"""),
        )
        compose.setContent { BlueprintRenderer(screen, blueprint, onActionEvent = { events += it }) }
        compose.onNodeWithText("old").performClick()
        compose.runOnIdle {
            screen = blueprint.decodeScreen("""{"body":{"type":"tap","text":"new","action":{"type":"go","destination":"x"}}}""")
        }
        compose.onNodeWithText("new").performClick()
        assertEquals(listOf("old", "new"), events.map { (it.source as EvTap).text })
    }

    @Test
    fun toStringNamesClassesOnlyAndNeverPayloadText() {
        val event = ActionEvent(Go("secret-destination"), EvTap("secret label", Go("secret-destination")))
        val text = event.toString()
        assertEquals("ActionEvent(action=Go, source=EvTap)", text)
        assertFalse(text.contains("secret"))
    }
}
