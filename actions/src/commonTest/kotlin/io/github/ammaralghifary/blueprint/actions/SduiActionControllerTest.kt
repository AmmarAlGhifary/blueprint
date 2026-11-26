package io.github.ammaralghifary.blueprint.actions

import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.ActionStatus
import io.github.ammaralghifary.blueprint.model.NavigateAction
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.UnknownAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One test per row of the lifecycle documented on [SduiActionController].
 *
 * @author Ammar Al Ghifary
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SduiActionControllerTest {
    private val submit = SubmitAction("form", mapOf("a" to "1"))
    private val url = OpenUrlAction("https://example.com")

    private fun event(action: SduiAction) = ActionEvent(action, Trigger(action))

    private fun TestScope.controllerScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    /** A handler the test completes by hand. */
    private class Pending : SduiActionHandler {
        val calls = mutableListOf<ActionEvent>()
        private val results = mutableListOf<CompletableDeferred<ActionResult>>()

        override suspend fun handle(event: ActionEvent): ActionResult {
            calls += event
            return CompletableDeferred<ActionResult>().also { results += it }.await()
        }

        fun finish(result: ActionResult, call: Int = results.lastIndex) {
            results[call].complete(result)
        }
    }

    @Test
    fun anActionNeverDispatchedIsIdle() = runTest {
        val controller = SduiActionController(controllerScope(), Pending())
        assertEquals(ActionStatus.Idle, controller.statusOf(submit))
        assertNull(controller.stateValue("k"))
    }

    @Test
    fun dispatchIsRunningAndStartsTheHandlerBeforeReturning() = runTest {
        val handler = Pending()
        val controller = SduiActionController(controllerScope(), handler)
        controller.onActionEvent(event(submit))
        // No advancing of the dispatcher: the launch is undispatched.
        assertEquals(1, handler.calls.size)
        assertEquals(ActionStatus.Running, controller.statusOf(submit))
    }

    @Test
    fun aHandlerThatDoesNotSuspendHasFinishedWhenDispatchReturns() = runTest {
        val controller = SduiActionController(controllerScope(), { ActionResult.Done })
        controller.onActionEvent(event(submit))
        assertEquals(ActionStatus.Done, controller.statusOf(submit))
    }

    @Test
    fun doneAndFailedAreReportedAsSuch() = runTest {
        for ((result, status) in listOf(ActionResult.Done to ActionStatus.Done, ActionResult.Failed to ActionStatus.Failed)) {
            val handler = Pending()
            val controller = SduiActionController(controllerScope(), handler)
            controller.onActionEvent(event(submit))
            handler.finish(result)
            advanceUntilIdle()
            assertEquals(status, controller.statusOf(submit))
        }
    }

    @Test
    fun anEqualActionIsNotHandledAgainWhileRunningButAnotherOneIs() = runTest {
        val handler = Pending()
        val controller = SduiActionController(controllerScope(), handler)
        controller.onActionEvent(event(submit))
        controller.onActionEvent(event(submit.copy()))
        assertEquals(1, handler.calls.size)
        controller.onActionEvent(event(url))
        assertEquals(2, handler.calls.size)
    }

    @Test
    fun equalActionsShareOneStatus() = runTest {
        val controller = SduiActionController(controllerScope(), { ActionResult.Done })
        controller.onActionEvent(event(submit))
        assertEquals(ActionStatus.Done, controller.statusOf(SubmitAction("form", mapOf("a" to "1"))))
        assertEquals(ActionStatus.Idle, controller.statusOf(SubmitAction("form")))
    }

    @Test
    fun aFinishedStatusLastsUntilTheActionIsDispatchedAgain() = runTest {
        val handler = Pending()
        val controller = SduiActionController(controllerScope(), handler)
        controller.onActionEvent(event(submit))
        handler.finish(ActionResult.Failed)
        advanceUntilIdle()
        testScheduler.advanceTimeBy(60.seconds)
        assertEquals(ActionStatus.Failed, controller.statusOf(submit))
        controller.onActionEvent(event(submit))
        assertEquals(ActionStatus.Running, controller.statusOf(submit))
        assertEquals(2, handler.calls.size)
    }

    @Test
    fun resetForgetsFinishedStatusesOnly() = runTest {
        val handler = Pending()
        val controller = SduiActionController(controllerScope(), handler)
        controller.setState("k", JsonPrimitive(1))
        controller.onActionEvent(event(submit))
        handler.finish(ActionResult.Done)
        advanceUntilIdle()
        controller.onActionEvent(event(url))
        controller.reset()
        assertEquals(ActionStatus.Idle, controller.statusOf(submit))
        assertEquals(ActionStatus.Running, controller.statusOf(url))
        assertEquals(JsonPrimitive(1), controller.stateValue("k"))
        // Still running, so still not handled twice.
        controller.onActionEvent(event(url))
        assertEquals(2, handler.calls.size)
    }

    @Test
    fun anUnhandledSetStateIsWrittenByTheController() = runTest {
        val controller = SduiActionController(controllerScope(), { ActionResult.NotHandled })
        val set = SetStateAction("tab", JsonPrimitive("news"))
        controller.onActionEvent(event(set))
        assertEquals(ActionStatus.Done, controller.statusOf(set))
        assertEquals(JsonPrimitive("news"), controller.stateValue("tab"))
        assertEquals(mapOf("tab" to JsonPrimitive("news")), controller.stateValues)

        controller.onActionEvent(event(SetStateAction("tab", null)))
        assertNull(controller.stateValue("tab"))
        assertTrue(controller.stateValues.isEmpty())
    }

    @Test
    fun anyOtherUnhandledActionGoesBackToIdleAndChangesNothing() = runTest {
        val controller = SduiActionController(controllerScope(), { ActionResult.NotHandled })
        val unhandled = listOf(url, submit, NavigateAction("home"), UnknownAction("teleport", JsonObject(emptyMap())))
        for (action in unhandled) {
            controller.onActionEvent(event(action))
            assertEquals(ActionStatus.Idle, controller.statusOf(action))
        }
        assertTrue(controller.stateValues.isEmpty())
    }

    @Test
    fun aSetStateTheHostHandlesIsNotWritten() = runTest {
        for (result in listOf(ActionResult.Done, ActionResult.Failed)) {
            val controller = SduiActionController(controllerScope(), { result })
            val set = SetStateAction("tab", JsonPrimitive("news"))
            controller.onActionEvent(event(set))
            assertNull(controller.stateValue("tab"))
            assertTrue(controller.statusOf(set) != ActionStatus.Running)
        }
    }

    @Test
    fun aThrowingHandlerIsFailedAndReported() = runTest {
        val boom = IllegalStateException("boom")
        val reported = mutableListOf<Pair<ActionEvent, Throwable>>()
        val controller = SduiActionController(controllerScope(), { throw boom }, { e, t -> reported += e to t })
        val dispatched = event(submit)
        controller.onActionEvent(dispatched)
        assertEquals(ActionStatus.Failed, controller.statusOf(submit))
        assertSame(dispatched, reported.single().first)
        assertSame(boom, reported.single().second)
    }

    @Test
    fun aThrowingErrorReporterIsIgnoredAndTheControllerKeepsWorking() = runTest {
        var calls = 0
        val controller = SduiActionController(
            controllerScope(),
            { calls++; error("handler") },
            { _, _ -> error("reporter") },
        )
        controller.onActionEvent(event(submit))
        controller.onActionEvent(event(submit))
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(ActionStatus.Failed, controller.statusOf(submit))
    }

    @Test
    fun aTimeoutInsideTheHandlerIsAFailure() = runTest {
        val reported = mutableListOf<Throwable>()
        val controller = SduiActionController(
            controllerScope(),
            { withTimeout(1.seconds) { awaitCancellation() } },
            { _, t -> reported += t },
        )
        controller.onActionEvent(event(submit))
        advanceUntilIdle()
        assertEquals(ActionStatus.Failed, controller.statusOf(submit))
        assertEquals(1, reported.size)
    }

    @Test
    fun cancellingTheScopeReturnsARunningActionToIdle() = runTest {
        val reported = mutableListOf<Throwable>()
        val scope = controllerScope()
        val controller = SduiActionController(scope, Pending(), { _, t -> reported += t })
        controller.onActionEvent(event(submit))
        scope.cancel()
        advanceUntilIdle()
        assertEquals(ActionStatus.Idle, controller.statusOf(submit))
        assertTrue(reported.isEmpty(), "cancellation is not a handler error")
    }

    @Test
    fun aHandlerThatSwallowsTheCancellationStillEndsIdle() = runTest {
        val scope = controllerScope()
        val controller = SduiActionController(scope, {
            runCatching { awaitCancellation() }
            ActionResult.Done
        })
        controller.onActionEvent(event(submit))
        scope.cancel()
        advanceUntilIdle()
        assertEquals(ActionStatus.Idle, controller.statusOf(submit))
    }

    @Test
    fun aHandlerThatCancelsItselfEndsIdle() = runTest {
        val controller = SduiActionController(controllerScope(), {
            currentCoroutineContext().cancel()
            awaitCancellation()
        })
        controller.onActionEvent(event(submit))
        advanceUntilIdle()
        assertEquals(ActionStatus.Idle, controller.statusOf(submit))
    }

    @Test
    fun nothingIsHandledOnACancelledScope() = runTest {
        val handler = Pending()
        val scope = controllerScope()
        val controller = SduiActionController(scope, handler)
        scope.cancel()
        controller.onActionEvent(event(submit))
        assertTrue(handler.calls.isEmpty())
        assertEquals(ActionStatus.Idle, controller.statusOf(submit))
    }

    @Test
    fun twoControllersShareNothing() = runTest {
        val firstHandler = Pending()
        val secondHandler = Pending()
        val scope = controllerScope()
        val first = SduiActionController(scope, firstHandler)
        val second = SduiActionController(scope, secondHandler)
        first.onActionEvent(event(submit))
        first.setState("k", JsonPrimitive(1))
        assertEquals(ActionStatus.Idle, second.statusOf(submit))
        assertNull(second.stateValue("k"))
        second.onActionEvent(event(submit))
        assertEquals(1, secondHandler.calls.size)
        secondHandler.finish(ActionResult.Failed)
        advanceUntilIdle()
        assertEquals(ActionStatus.Running, first.statusOf(submit))
        assertEquals(ActionStatus.Failed, second.statusOf(submit))
    }

    @Test
    fun settingNullOrJsonNullRemovesTheKey() = runTest {
        val controller = SduiActionController(controllerScope(), Pending())
        for (nothing in listOf(null, JsonNull)) {
            controller.setState("k", JsonPrimitive("v"))
            controller.setState("k", nothing)
            assertTrue(controller.stateValues.isEmpty())
        }
    }

    @Test
    fun stateValuesIsACopy() = runTest {
        val controller = SduiActionController(controllerScope(), Pending())
        val before = controller.stateValues
        controller.setState("k", JsonPrimitive(1))
        assertTrue(before.isEmpty())
    }
}
