package io.github.ammaralghifary.blueprint.model

import io.github.ammaralghifary.blueprint.render.BlueprintRenderer

/**
 * An [action] a user triggered and the component that fired it, handed to the `onActionEvent`
 * callback of [BlueprintRenderer]. Use [source] to tell apart two buttons that carry the same
 * action, or to attribute an action to the node that produced it.
 *
 * [source] is the component whose renderer called `scope.dispatch`, so a button nested in a card
 * reports the button, and a tappable card reports the card.
 *
 * Both [action] and [source] carry payload text (destinations, params, labels). [toString] names only
 * their classes and is safe to log; do not forward the objects themselves to analytics or a crash
 * reporter.
 *
 * @author Ammar Al Ghifary
 */
public class ActionEvent(
    /** The action that was dispatched. It carries payload text. */
    public val action: SduiAction,
    /** The component whose renderer dispatched it. It carries payload text. */
    public val source: SduiComponent,
) {
    override fun equals(other: Any?): Boolean =
        other is ActionEvent && action == other.action && source == other.source

    override fun hashCode(): Int = 31 * action.hashCode() + source.hashCode()

    /** Class names only; the action and the source are payload-bearing. */
    override fun toString(): String =
        "ActionEvent(action=${action::class.simpleName}, source=${source::class.simpleName})"
}
