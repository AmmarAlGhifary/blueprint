package io.github.ammaralghifary.blueprint.defaults

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.ActionStatus
import io.github.ammaralghifary.blueprint.model.NavigateAction
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import io.github.ammaralghifary.blueprint.render.LocalSduiActionContext
import io.github.ammaralghifary.blueprint.render.SduiActionContext
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The stock pack against :core's contract alone, with a hand-driven context: :defaults knows no controller.
/** @author Ammar Al Ghifary */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultsActionStatusTest {
    @get:Rule
    val compose = createComposeRule()

    private val statuses = mutableStateMapOf<SduiAction, ActionStatus>()
    private val context = object : SduiActionContext {
        override fun statusOf(action: SduiAction) = statuses[action] ?: ActionStatus.Idle
        override fun stateValue(key: String): JsonPrimitive? = null
    }

    private val blueprint = Blueprint(DefaultSduiRegistry)
    private val fired = mutableListOf<SduiAction>()
    private val save = NavigateAction("save")
    private val open = NavigateAction("open")

    private fun show(json: String, provide: Boolean = true) {
        val screen = blueprint.decodeScreen(json)
        compose.setContent {
            val content = @androidx.compose.runtime.Composable { BlueprintRenderer(screen, blueprint) { fired += it } }
            if (provide) CompositionLocalProvider(LocalSduiActionContext provides context, content = content) else content()
        }
    }

    private fun button(text: String, destination: String, variant: String = "filled") =
        """{"type":"button","text":"$text","variant":"$variant","action":{"type":"navigate","destination":"$destination"}}"""

    private fun progress() = compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)

    private fun set(action: SduiAction, status: ActionStatus) {
        compose.runOnUiThread {
            statuses[action] = status
            // With the clock paused, nothing else announces the write to the composition.
            Snapshot.sendApplyNotifications()
        }
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
    }

    @Test
    fun everyVariantIsDisabledWhileRunningAndOnlyThen() {
        show(
            """{"body":{"type":"column","children":[
                ${button("Filled", "save")},${button("Outlined", "save", "outlined")},${button("Text", "save", "text")},
                ${button("Other", "open")}]}}""",
        )
        for (status in listOf(ActionStatus.Idle, ActionStatus.Done, ActionStatus.Failed, ActionStatus.Running, ActionStatus.Done)) {
            set(save, status)
            for (label in listOf("Filled", "Outlined", "Text")) {
                val node = compose.onNodeWithText(label)
                if (status == ActionStatus.Running) node.assertIsNotEnabled() else node.assertIsEnabled()
            }
            compose.onNodeWithText("Other").assertIsEnabled()
        }
    }

    @Test
    fun theProgressIndicatorAppearsOnlyAfterTheDelay() {
        show("""{"body":${button("Save", "save")}}""")
        compose.mainClock.autoAdvance = false
        set(save, ActionStatus.Running)
        advance(100)
        compose.onNodeWithText("Save").assertIsNotEnabled()
        progress().assertCountEquals(0)
        advance(150)
        progress().assertCountEquals(0)
        advance(150)
        progress().assertCountEquals(1)

        set(save, ActionStatus.Done)
        advance(50)
        progress().assertCountEquals(0)
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun aQuickActionNeverShowsTheIndicator() {
        show("""{"body":${button("Save", "save")}}""")
        compose.mainClock.autoAdvance = false
        set(save, ActionStatus.Running)
        advance(150)
        compose.onNodeWithText("Save").assertIsNotEnabled()
        set(save, ActionStatus.Done)
        advance(500)
        progress().assertCountEquals(0)
        // A second run waits the full delay again.
        set(save, ActionStatus.Running)
        advance(200)
        compose.onNodeWithText("Save").assertIsNotEnabled()
        progress().assertCountEquals(0)
    }

    @Test
    fun aCardIsDisabledWhileItsActionRuns() {
        show("""{"body":{"type":"card","action":{"type":"navigate","destination":"open"},"children":[{"type":"text","text":"Card"}]}}""")
        set(open, ActionStatus.Running)
        compose.onNodeWithText("Card").assertIsNotEnabled().performClick()
        assertEquals(emptyList<SduiAction>(), fired)
        set(open, ActionStatus.Done)
        compose.onNodeWithText("Card").assertIsEnabled().performClick()
        assertEquals(listOf<SduiAction>(open), fired)
    }

    @Test
    fun tappingABusyButtonInsideACardDoesNotFireTheCard() {
        show(
            """{"body":{"type":"card","action":{"type":"navigate","destination":"open"},
                "children":[${button("Save", "save")}]}}""",
        )
        set(save, ActionStatus.Running)
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<SduiAction>(), fired)
    }

    @Test
    fun tappingABusyCardInsideACardDoesNotFireTheOuterOne() {
        show(
            """{"body":{"type":"card","action":{"type":"navigate","destination":"open"},"children":[
                {"type":"card","action":{"type":"navigate","destination":"save"},"children":[{"type":"text","text":"Inner"}]}]}}""",
        )
        set(save, ActionStatus.Running)
        compose.onNodeWithText("Inner").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<SduiAction>(), fired)
    }

    @Test
    fun withoutAContextNothingChanges() {
        statuses[save] = ActionStatus.Running
        show("""{"body":${button("Save", "save")}}""", provide = false)
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.onNodeWithText("Save").performClick()
        progress().assertCountEquals(0)
        assertEquals(listOf<SduiAction>(save, save), fired)
    }
}
