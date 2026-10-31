package io.github.ammaralghifary.blueprint.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent

/** Handed to every renderer so containers can recurse and components can fire actions. */
public interface SduiScope {
    /** Renders [component] through the registry (server modifier applied, fallback if unknown). */
    @Composable
    public fun Render(component: SduiComponent, modifier: Modifier = Modifier)

    /**
     * Renders [children] in order, each keyed by position and class so its `remember`ed state
     * survives a refetch with the same tree shape.
     *
     * [childModifier] hands down the scope-only part of the server modifier (`weight`, `alignment`),
     * which only the enclosing Row/Column can resolve.
     */
    // TODO key by a node id once the payload schema carries one, so a server reorder keeps state
    @Composable
    public fun RenderAll(
        children: List<SduiComponent>,
        childModifier: (SduiComponent) -> Modifier = { Modifier },
    )

    /**
     * Forwards [action] to the host: `onActionEvent` (with the component that owns this scope as the
     * source) and `onAction`, as given to [BlueprintRenderer].
     */
    public fun dispatch(action: SduiAction)
}
