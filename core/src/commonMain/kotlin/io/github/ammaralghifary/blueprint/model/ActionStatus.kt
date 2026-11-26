package io.github.ammaralghifary.blueprint.model

/**
 * Where an action is in its lifecycle, as reported by a
 * [io.github.ammaralghifary.blueprint.render.SduiActionContext].
 *
 * @author Ammar Al Ghifary
 */
public enum class ActionStatus {
    /** Never dispatched, not handled by anyone, cancelled or reset. */
    Idle,

    /** Dispatched and still being handled. */
    Running,

    /** Handled successfully. */
    Done,

    /** Handling it failed. */
    Failed,
}
