package io.github.ammaralghifary.blueprint.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.ActionStatus
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.render.LocalSduiActionContext
import io.github.ammaralghifary.blueprint.render.SduiActionContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Runs actions through a [SduiActionHandler] and remembers how each one is doing, plus the state
 * written by `set_state`. Pass [onActionEvent] to `BlueprintRenderer` and wrap it in
 * [ProvideSduiActions]:
 *
 * ```
 * val scope = rememberCoroutineScope()
 * val controller = remember { SduiActionController(scope, handler) }
 * ProvideSduiActions(controller) {
 *     BlueprintRenderer(screen, blueprint, onActionEvent = controller::onActionEvent)
 * }
 * ```
 *
 * One action goes through these states:
 * - dispatched: [ActionStatus.Running]. While it is, dispatching an equal action does nothing here.
 * - the handler returns [ActionResult.Done] or [ActionResult.Failed], or throws (Failed): it keeps
 *   that status until it is dispatched again or [reset] is called.
 * - the handler returns [ActionResult.NotHandled]: a [SetStateAction] is applied and becomes
 *   [ActionStatus.Done]; anything else goes back to [ActionStatus.Idle] and nothing happens.
 * - [scope] is cancelled: [ActionStatus.Idle].
 *
 * Actions that are equal share one status, because the payload carries no node id yet. An action class
 * of your own should therefore be a data class. Nothing is persisted and nothing is logged. Use one
 * controller from the UI thread.
 *
 * @author Ammar Al Ghifary
 */
public class SduiActionController(
    /** Handlers run in this scope; cancelling it cancels them. */
    private val scope: CoroutineScope,
    private val handler: SduiActionHandler,
    /**
     * Told when [handler] throws, so you can report it. The event and possibly the exception carry
     * payload text. If this throws too, that is ignored.
     */
    private val onHandlerError: (ActionEvent, Throwable) -> Unit = { _, _ -> },
) : SduiActionContext {
    private val statuses = mutableStateMapOf<SduiAction, ActionStatus>()
    private val state = mutableStateMapOf<String, JsonPrimitive>()

    /** A copy of everything `set_state` and [setState] have stored. The values are payload text. */
    public val stateValues: Map<String, JsonPrimitive> get() = state.toMap()

    override fun statusOf(action: SduiAction): ActionStatus = statuses[action] ?: ActionStatus.Idle

    override fun stateValue(key: String): JsonPrimitive? = state[key]

    /** Stores [value] under [key]; null (or JSON null) removes the key. */
    public fun setState(key: String, value: JsonPrimitive?) {
        if (value == null || value === JsonNull) state.remove(key) else state[key] = value
    }

    /**
     * Forgets every [ActionStatus.Done] and [ActionStatus.Failed]. Actions still running stay
     * [ActionStatus.Running], and stored state is kept.
     */
    public fun reset() {
        statuses.filterValues { it != ActionStatus.Running }.keys.forEach { statuses.remove(it) }
    }

    /** Hands [event] to the handler, unless an equal action is still running or [scope] is cancelled. */
    public fun onActionEvent(event: ActionEvent) {
        val action = event.action
        if (statuses[action] == ActionStatus.Running) return
        if (!scope.isActive) {
            statuses.remove(action)
            return
        }
        statuses[action] = ActionStatus.Running
        // Undispatched, so a handler that does not suspend has finished before this returns.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = try {
                handler.handle(event)
            } catch (e: Throwable) {
                if (e is CancellationException && !isActive) {
                    statuses.remove(action)
                    throw e
                }
                // A timeout inside the handler is also a CancellationException, but a failure here.
                fail(event, e)
                return@launch
            }
            // A handler that swallowed the cancellation did not finish its work.
            if (!isActive) {
                statuses.remove(action)
                return@launch
            }
            finish(action, result)
        }
    }

    private fun finish(action: SduiAction, result: ActionResult) {
        when (result) {
            ActionResult.Done -> statuses[action] = ActionStatus.Done
            ActionResult.Failed -> statuses[action] = ActionStatus.Failed
            ActionResult.NotHandled -> if (action is SetStateAction) {
                setState(action.key, action.value)
                statuses[action] = ActionStatus.Done
            } else {
                statuses.remove(action)
            }
        }
    }

    private fun fail(event: ActionEvent, error: Throwable) {
        statuses[event.action] = ActionStatus.Failed
        try {
            onHandlerError(event, error)
        } catch (e: Exception) {
            // dropped on purpose: an error reporter must not take the scope down
        }
    }
}

/** Makes [controller] the action context of [content], which stock buttons and cards read. */
@Composable
public fun ProvideSduiActions(controller: SduiActionController, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSduiActionContext provides controller, content = content)
}
