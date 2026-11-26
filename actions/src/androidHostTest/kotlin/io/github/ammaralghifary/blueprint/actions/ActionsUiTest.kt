package io.github.ammaralghifary.blueprint.actions

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.defaults.DefaultSduiRegistry
import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.ActionStatus
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import io.github.ammaralghifary.blueprint.render.LocalSduiActionContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A host component that shows one state value, the way set_state is meant to be read. */
@Serializable
data class StateLabel(val key: String, override val modifier: SduiModifier? = null) : SduiComponent

/** @author Ammar Al Ghifary */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val blueprint = Blueprint(
        sduiRegistry {
            include(DefaultSduiRegistry)
            sduiActions()
            component<StateLabel>("state_label") { c, _, _ ->
                val value = LocalSduiActionContext.current?.stateValue(c.key)?.content
                BasicText("value: ${value ?: "none"}")
            }
        },
    )

    private val legacy = mutableListOf<SduiAction>()
    private lateinit var controller: SduiActionController

    private fun show(json: String, handler: SduiActionHandler) {
        val screen = blueprint.decodeScreen(json)
        compose.setContent {
            val scope = rememberCoroutineScope()
            controller = androidx.compose.runtime.remember { SduiActionController(scope, handler) }
            ProvideSduiActions(controller) {
                BlueprintRenderer(screen, blueprint, onActionEvent = controller::onActionEvent) { legacy += it }
            }
        }
    }

    private val submitButton = """{"body":{"type":"button","text":"Send","action":{"type":"submit","target":"form"}}}"""

    @Test
    fun theStockButtonIsDisabledWhileItsActionRunsAndEnabledAfterwards() {
        val result = CompletableDeferred<ActionResult>()
        val calls = mutableListOf<ActionEvent>()
        show(submitButton) { calls += it; result.await() }

        compose.onNodeWithText("Send").assertIsEnabled().performClick()
        compose.onNodeWithText("Send").assertIsNotEnabled().performClick()
        assertEquals(1, calls.size)
        assertEquals(listOf<SduiAction>(SubmitAction("form")), legacy)

        compose.runOnIdle { result.complete(ActionResult.Failed) }
        compose.onNodeWithText("Send").assertIsEnabled()
        assertEquals(ActionStatus.Failed, controller.statusOf(SubmitAction("form")))
    }

    @Test
    fun theLegacyOnActionIsStillCalledForEveryDispatch() {
        show(submitButton) { ActionResult.NotHandled }
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Send").performClick()
        assertEquals(List<SduiAction>(2) { SubmitAction("form") }, legacy)
    }

    @Test
    fun aComponentRecomposesWhenSetStateChangesWhatItReads() {
        show(
            """{"body":{"type":"column","children":[
                {"type":"state_label","key":"tab"},
                {"type":"button","text":"News","action":{"type":"set_state","key":"tab","value":"news"}},
                {"type":"button","text":"Clear","action":{"type":"set_state","key":"tab","value":null}}]}}""",
        ) { ActionResult.NotHandled }

        compose.onNodeWithText("value: none").assertExists()
        compose.onNodeWithText("News").performClick()
        compose.onNodeWithText("value: news").assertExists()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("value: none").assertExists()
        // The same action fires again from its Done status.
        compose.onNodeWithText("News").performClick()
        compose.onNodeWithText("value: news").assertExists()
    }

    @Test
    fun aValueSetByTheHostShowsUp() {
        show("""{"body":{"type":"state_label","key":"tab"}}""") { ActionResult.NotHandled }
        compose.runOnIdle { controller.setState("tab", JsonPrimitive("home")) }
        compose.onNodeWithText("value: home").assertExists()
    }
}
