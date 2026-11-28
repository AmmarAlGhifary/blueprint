package io.github.ammaralghifary.blueprint.defaults

import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.ActionStatus
import io.github.ammaralghifary.blueprint.model.NavigateAction
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import io.github.ammaralghifary.blueprint.render.LocalSduiActionContext
import io.github.ammaralghifary.blueprint.render.SduiActionContext
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** An action of a host's own, which no registry knows. */
private data object Reload : SduiAction

/** @author Ammar Al Ghifary */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultsDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val blueprint = Blueprint(DefaultSduiRegistry)
    private val events = mutableListOf<ActionEvent>()
    private val retry = NavigateAction("reload")
    private val back = NavigateAction("back")
    private val gone = NavigateAction("gone")

    private fun json(extra: String = "") =
        """{"body":{"type":"dialog","title":"No connection","message":"Try again.",
            "primary":{"label":"Retry","action":{"type":"navigate","destination":"reload"}},
            "secondary":{"label":"Close"}$extra}}"""

    private val onDismiss = ""","on_dismiss":{"type":"navigate","destination":"gone"}"""

    private fun show(payload: String) {
        val screen = blueprint.decodeScreen(payload)
        compose.setContent { BlueprintRenderer(screen, blueprint, onActionEvent = { events += it }) }
    }

    private fun assertOpen() = compose.onNodeWithText("Retry").assertIsDisplayed()

    private fun assertClosed() {
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText("No connection").assertDoesNotExist()
    }

    private val fired get() = events.map { it.action }

    private fun pressBack() {
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            ShadowDialog.getLatestDialog().onBackPressed()
        }
        compose.waitForIdle()
    }

    private fun tapOutside() {
        compose.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 0, action, -50f, -50f, 0)
                dialog.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun showsTitleMessageAndBothLabels() {
        show(json())
        for (text in listOf("No connection", "Try again.", "Retry", "Close")) compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun aMinimalDialogShowsItsOneButton() {
        show("""{"body":{"type":"dialog","primary":{"label":"OK"}}}""")
        compose.onNodeWithText("OK").assertIsDisplayed().performClick()
        compose.onNodeWithText("OK").assertDoesNotExist()
    }

    @Test
    fun aButtonWithoutAnActionClosesAndDispatchesNothing() {
        show(json(onDismiss))
        compose.onNodeWithText("Close").performClick()
        assertClosed()
        assertEquals(emptyList<SduiAction>(), fired)
    }

    @Test
    fun withoutAContextAButtonDispatchesFromTheDialogAndCloses() {
        show(json(onDismiss))
        compose.onNodeWithText("Retry").performClick()
        assertClosed()
        // on_dismiss belongs to dismissing, not to the buttons.
        assertEquals(listOf<SduiAction>(retry), fired)
        assertEquals("Retry", (events.single().source as SduiDialog).primary.label)
    }

    @Test
    fun aSecondaryButtonWithAnActionDispatchesItAndCloses() {
        show(
            """{"body":{"type":"dialog","primary":{"label":"Retry"},
                "secondary":{"label":"Back","action":{"type":"navigate","destination":"back"}}}}""",
        )
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Back").assertDoesNotExist()
        assertEquals(listOf<SduiAction>(back), fired)
    }

    @Test
    fun aBackPressDismissesADismissibleDialog() {
        show(json(onDismiss))
        pressBack()
        assertClosed()
        assertEquals(listOf<SduiAction>(gone), fired)
    }

    @Test
    fun anOutsideTapDismissesADismissibleDialog() {
        show(json(onDismiss))
        tapOutside()
        assertClosed()
        assertEquals(listOf<SduiAction>(gone), fired)
    }

    @Test
    fun dismissingWithoutOnDismissOnlyCloses() {
        show(json())
        pressBack()
        assertClosed()
        assertEquals(emptyList<SduiAction>(), fired)
    }

    @Test
    fun aDialogThatIsNotDismissibleIgnoresBackAndOutsideTaps() {
        show(json(""","dismissible":false$onDismiss"""))
        pressBack()
        assertOpen()
        tapOutside()
        assertOpen()
        assertEquals(emptyList<SduiAction>(), fired)
        compose.onNodeWithText("Close").performClick()
        assertClosed()
    }

    @Test
    fun aClosedDialogStaysClosedForAnEqualNodeAndADifferentOneShows() {
        val first = blueprint.decodeScreen(json())
        var screen by mutableStateOf<SduiScreen>(first)
        compose.setContent { BlueprintRenderer(screen, blueprint) }
        compose.onNodeWithText("Close").performClick()
        assertClosed()

        // A refetch of the same payload: equal, but another instance.
        compose.runOnUiThread { screen = blueprint.decodeScreen(json()) }
        assertClosed()

        compose.runOnUiThread { screen = blueprint.decodeScreen(json().replace("Try again.", "Still offline.")) }
        compose.onNodeWithText("Still offline.").assertIsDisplayed()

        compose.runOnUiThread { screen = blueprint.decodeScreen(json()) }
        compose.onNodeWithText("Try again.").assertIsDisplayed()
    }

    @Test
    fun aClosedDialogStaysClosedAfterTheStateIsRestored() {
        val screen = blueprint.decodeScreen(json())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BlueprintRenderer(screen, blueprint) }
        compose.onNodeWithText("Close").performClick()
        restoration.emulateSavedInstanceStateRestore()
        assertClosed()
    }

    @Test
    fun theDialogTakesNoSpaceWhereItSits() {
        show(
            """{"body":{"type":"column","arrangement":"start","children":[
                {"type":"text","text":"above"},
                {"type":"dialog","primary":{"label":"OK"},"modifier":{"padding":{"all":40},"height":{"type":"exact","value":200}}},
                {"type":"text","text":"below"}]}}""",
        )
        val above = compose.onNodeWithText("above").getUnclippedBoundsInRoot()
        val below = compose.onNodeWithText("below").getUnclippedBoundsInRoot()
        assertEquals(above.bottom.value, below.top.value, 0.5f)
    }

    // disabled_seconds. The clock is driven by hand.

    private fun waiting(seconds: Int) =
        SduiDialog(primary = SduiDialogButton("Retry", Reload, disabledSeconds = seconds), secondary = SduiDialogButton("Close"))

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
    }

    @Test
    fun thePrimaryCountsDownAndIsDisabledUntilItEnds() {
        compose.mainClock.autoAdvance = false
        val actions = mutableListOf<SduiAction>()
        compose.setContent { BlueprintDialog(waiting(3), onDismissRequest = {}, onAction = { actions += it }) }
        advance(100)
        compose.onNodeWithText("Retry (3)").assertIsNotEnabled().performClick()
        // The other button does not wait.
        compose.onNodeWithText("Close").assertIsEnabled()
        advance(1_000)
        compose.onNodeWithText("Retry (2)").assertIsNotEnabled()
        advance(1_000)
        compose.onNodeWithText("Retry (1)").assertIsNotEnabled()
        advance(1_000)
        compose.onNodeWithText("Retry").assertIsEnabled()
        assertEquals(emptyList<SduiAction>(), actions)
        compose.onNodeWithText("Retry").performClick()
        advance(100)
        assertEquals(listOf<SduiAction>(Reload), actions)
    }

    @Test
    fun zeroAndNegativeWaitsMeanNone() {
        var dialog by mutableStateOf(waiting(0))
        compose.setContent { BlueprintDialog(dialog, onDismissRequest = {}) }
        compose.onNodeWithText("Retry").assertIsEnabled()
        for (seconds in listOf(-5, Int.MIN_VALUE)) {
            compose.runOnUiThread { dialog = waiting(seconds) }
            compose.onNodeWithText("Retry").assertIsEnabled()
        }
    }

    @Test
    fun aWaitIsClampedToTenMinutes() {
        compose.mainClock.autoAdvance = false
        compose.setContent { BlueprintDialog(waiting(Int.MAX_VALUE), onDismissRequest = {}) }
        advance(100)
        compose.onNodeWithText("Retry (600)").assertIsNotEnabled()
    }

    @Test
    fun theSecondaryCanWaitToo() {
        compose.mainClock.autoAdvance = false
        val dialog = SduiDialog(primary = SduiDialogButton("OK"), secondary = SduiDialogButton("Later", disabledSeconds = 1))
        compose.setContent { BlueprintDialog(dialog, onDismissRequest = {}) }
        advance(100)
        compose.onNodeWithText("Later (1)").assertIsNotEnabled()
        compose.onNodeWithText("OK").assertIsEnabled()
        advance(1_000)
        compose.onNodeWithText("Later").assertIsEnabled()
    }

    @Test
    fun theCountdownGoesOnWhereItWasAfterTheStateIsRestored() {
        compose.mainClock.autoAdvance = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BlueprintDialog(waiting(10), onDismissRequest = {}) }
        advance(3_100)
        compose.onNodeWithText("Retry (7)").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        advance(100)
        compose.onNodeWithText("Retry (7)").assertIsNotEnabled()
    }

    // BlueprintDialog on its own: no Blueprint, no registry, the host's own action and visibility.

    @Test
    fun aHostBuiltDialogReportsItsActionAndAsksToClose() {
        val actions = mutableListOf<SduiAction>()
        var closeRequests = 0
        var visible by mutableStateOf(true)
        val dialog = SduiDialog(title = "Offline", primary = SduiDialogButton("Retry", Reload))
        compose.setContent {
            if (visible) {
                BlueprintDialog(dialog, onDismissRequest = { closeRequests++; visible = false }, onAction = { actions += it })
            }
        }
        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Offline").assertDoesNotExist()
        assertSame(Reload, actions.single())
        assertEquals(1, closeRequests)
    }

    @Test
    fun theHostOwnsTheVisibility() {
        var closeRequests = 0
        compose.setContent {
            BlueprintDialog(SduiDialog(primary = SduiDialogButton("OK")), onDismissRequest = { closeRequests++ })
        }
        compose.onNodeWithText("OK").performClick()
        // The host did not hide it, so it is still there.
        compose.onNodeWithText("OK").assertIsDisplayed()
        assertEquals(1, closeRequests)
    }

    // With a context driven by hand. The same rules with a real controller are tested in :actions.

    private val statuses = mutableStateMapOf<SduiAction, ActionStatus>()
    private val context = object : SduiActionContext {
        override fun statusOf(action: SduiAction) = statuses[action] ?: ActionStatus.Idle
        override fun stateValue(key: String): JsonPrimitive? = null
    }

    private fun showTracked(dialog: SduiDialog, onDispatch: (SduiAction) -> Unit) {
        var visible by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalSduiActionContext provides context) {
                Column {
                    if (visible) BlueprintDialog(dialog, onDismissRequest = { visible = false }, onAction = onDispatch)
                }
            }
        }
    }

    private val trackedDialog =
        SduiDialog(primary = SduiDialogButton("Retry", retry), secondary = SduiDialogButton("Back", back))

    @Test
    fun aRunningActionKeepsItsDialogOpenAndTheOtherButtonUsable() {
        showTracked(trackedDialog) { statuses[it] = ActionStatus.Running }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.onNodeWithText("Back").assertIsEnabled()
        compose.runOnUiThread { statuses[retry] = ActionStatus.Done }
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun anActionThatEndsIdleClosesTheDialog() {
        showTracked(trackedDialog) { statuses[it] = ActionStatus.Running }
        compose.onNodeWithText("Retry").performClick()
        compose.runOnUiThread { statuses.remove(retry) }
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun aContextThatDoesNotTrackTheActionLetsTheDialogClose() {
        showTracked(trackedDialog) {}
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }
}
