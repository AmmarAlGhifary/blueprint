package io.github.ammaralghifary.blueprint.actions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.defaults.BlueprintDialog
import io.github.ammaralghifary.blueprint.defaults.DefaultSduiRegistry
import io.github.ammaralghifary.blueprint.defaults.SduiDialog
import io.github.ammaralghifary.blueprint.defaults.SduiDialogButton
import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.ActionStatus
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The stock dialog driven by a real controller.
/** @author Ammar Al Ghifary */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialogActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val blueprint = Blueprint(
        sduiRegistry {
            include(DefaultSduiRegistry)
            sduiActions()
        },
    )

    private val retry = SubmitAction("retry")
    private val calls = mutableListOf<ActionEvent>()
    private val results = mutableListOf<CompletableDeferred<ActionResult>>()
    private lateinit var controller: SduiActionController

    /** Suspends until the test answers. */
    private val slow = SduiActionHandler { event ->
        calls += event
        CompletableDeferred<ActionResult>().also { results += it }.await()
    }

    private val payload =
        """{"body":{"type":"dialog","title":"Offline",
            "primary":{"label":"Retry","action":{"type":"submit","target":"retry"}},
            "secondary":{"label":"Log","action":{"type":"submit","target":"log"}}}}"""

    private fun show(handler: SduiActionHandler) {
        val screen = blueprint.decodeScreen(payload)
        compose.setContent {
            val scope = rememberCoroutineScope()
            controller = remember { SduiActionController(scope, handler) }
            ProvideSduiActions(controller) {
                BlueprintRenderer(screen, blueprint, onActionEvent = controller::onActionEvent)
            }
        }
    }

    private fun answer(result: ActionResult, call: Int = 0) {
        compose.runOnIdle { results[call].complete(result) }
    }

    private fun progress() = compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)

    private fun assertClosed() = compose.onNodeWithText("Offline").assertDoesNotExist()

    @Test
    fun theDialogStaysOpenWhileItsActionRunsAndClosesWhenDone() {
        show(slow)
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Log").assertIsEnabled()
        assertEquals(1, calls.size)
        assertTrue("the dialog is the source", calls.single().source is SduiDialog)
        answer(ActionResult.Done)
        assertClosed()
    }

    @Test
    fun theSpinnerAppearsOnlyAfterTheDelay() {
        show(slow)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Retry").performClick()
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        progress().assertCountEquals(0)
        compose.mainClock.advanceTimeBy(300)
        progress().assertCountEquals(1)
    }

    @Test
    fun aFailedActionLeavesTheDialogOpenToTryAgain() {
        show(slow)
        compose.onNodeWithText("Retry").performClick()
        answer(ActionResult.Failed)
        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsEnabled().performClick()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        assertEquals(2, calls.size)
        answer(ActionResult.Done, call = 1)
        assertClosed()
    }

    @Test
    fun aHandlerThatFailsAtOnceLeavesTheDialogOpenEveryTime() {
        show { calls += it; ActionResult.Failed }
        repeat(3) {
            compose.onNodeWithText("Retry").performClick()
            compose.onNodeWithText("Retry").assertIsEnabled()
        }
        compose.onNodeWithText("Offline").assertIsDisplayed()
        assertEquals(3, calls.size)
    }

    @Test
    fun aHandlerThatSucceedsAtOnceClosesTheDialog() {
        show { ActionResult.Done }
        compose.onNodeWithText("Retry").performClick()
        assertClosed()
    }

    @Test
    fun anUnhandledActionClosesTheDialog() {
        show { ActionResult.NotHandled }
        compose.onNodeWithText("Retry").performClick()
        assertClosed()
        assertEquals(ActionStatus.Idle, controller.statusOf(retry))
    }

    @Test
    fun theSecondaryCanFinishAndCloseWhileThePrimaryRuns() {
        show(slow)
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Log").performClick()
        compose.onNodeWithText("Log").assertIsNotEnabled()
        answer(ActionResult.Done, call = 1)
        assertClosed()
        assertEquals(ActionStatus.Running, controller.statusOf(retry))
    }

    @Test
    fun aFailedSecondaryLeavesThePrimaryAwaited() {
        show(slow)
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Log").performClick()
        answer(ActionResult.Failed, call = 1)
        compose.onNodeWithText("Offline").assertIsDisplayed()
        answer(ActionResult.Done, call = 0)
        assertClosed()
    }

    @Test
    fun aHostBuiltDialogWorksWithAControllerAndTheHostsOwnAction() {
        val dialog = SduiDialog(title = "Offline", primary = SduiDialogButton("Retry", HostReload))
        val handled = mutableListOf<SduiAction>()
        val result = CompletableDeferred<ActionResult>()
        var visible by mutableStateOf(true)
        compose.setContent {
            val scope = rememberCoroutineScope()
            val own = remember { SduiActionController(scope, { handled += it.action; result.await() }) }
            ProvideSduiActions(own) {
                if (visible) {
                    BlueprintDialog(
                        dialog,
                        onDismissRequest = { visible = false },
                        onAction = { own.onActionEvent(ActionEvent(it, dialog)) },
                    )
                }
            }
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.runOnIdle { result.complete(ActionResult.Done) }
        assertClosed()
        assertEquals(listOf<SduiAction>(HostReload), handled)
    }
}

/** An action of a host's own. */
private data object HostReload : SduiAction
