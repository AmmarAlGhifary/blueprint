package io.github.ammaralghifary.blueprint.actions

import io.github.ammaralghifary.blueprint.model.ActionEvent

/**
 * What a [SduiActionHandler] did with an action.
 *
 * @author Ammar Al Ghifary
 */
public enum class ActionResult {
    /** The host performed it. */
    Done,

    /** The host tried and could not. */
    Failed,

    /** Not the host's business. Only `set_state` then has an effect: the controller stores the value. */
    NotHandled,
}

/**
 * The host's side of every action. It always has the first say: the library never navigates, opens a
 * URL or sends data, whatever the answer.
 *
 * @author Ammar Al Ghifary
 */
public fun interface SduiActionHandler {
    /**
     * Handles [event] and says how it went. It is started on the thread that dispatched the action and
     * may suspend; move blocking work off that thread. Throwing counts as [ActionResult.Failed].
     * [event] carries payload text, so do not log it beyond its `toString()`.
     */
    public suspend fun handle(event: ActionEvent): ActionResult
}
