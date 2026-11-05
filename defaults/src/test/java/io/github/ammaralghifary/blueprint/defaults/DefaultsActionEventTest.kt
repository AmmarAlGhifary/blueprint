package io.github.ammaralghifary.blueprint.defaults

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.NavigateAction
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The stock components report themselves as the source without any change to their renderers.
 *
 * @author Ammar Al Ghifary
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultsActionEventTest {
    @get:Rule
    val compose = createComposeRule()

    private val blueprint = Blueprint(DefaultSduiRegistry)
    private val events = mutableListOf<ActionEvent>()

    private fun show(json: String) {
        val screen = blueprint.decodeScreen(json)
        compose.setContent { BlueprintRenderer(screen, blueprint, onActionEvent = { events += it }) }
    }

    @Test
    fun aButtonNestedInACardInAColumnReportsTheButton() {
        show(
            """{"body":{"type":"column","children":[
                {"type":"card","action":{"type":"navigate","destination":"card"},"children":[
                    {"type":"text","text":"card body"},
                    {"type":"button","text":"Buy","action":{"type":"navigate","destination":"buy"}}]}]}}""",
        )
        compose.onNodeWithText("Buy").performClick()
        // The card is clickable, so its semantics merge the children; click its corner, not the middle
        // where the button sits.
        compose.onNodeWithText("card body").performTouchInput { click(topLeft + Offset(2f, 2f)) }

        assertEquals(NavigateAction("buy"), events[0].action)
        assertEquals(SduiButton("Buy", NavigateAction("buy")), events[0].source)
        assertEquals(NavigateAction("card"), events[1].action)
        assertTrue(events[1].source is SduiCard)
        assertEquals(2, events.size)
    }

    @Test
    fun twoButtonsWithTheSameActionAreToldApart() {
        show(
            """{"body":{"type":"row","children":[
                {"type":"button","text":"One","action":{"type":"navigate","destination":"x"}},
                {"type":"button","text":"Two","action":{"type":"navigate","destination":"x"}}]}}""",
        )
        compose.onNodeWithText("Two").performClick()
        compose.onNodeWithText("One").performClick()
        assertEquals(listOf("Two", "One"), events.map { (it.source as SduiButton).text })
    }
}
