package io.github.ammaralghifary.blueprint.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent

/** Handed to every renderer so containers can recurse and components can fire actions. */
public interface SduiScope {
    /** Renders [component] through the registry (server modifier applied, fallback if unknown). */
    @Composable
    public fun Render(component: SduiComponent, modifier: Modifier)

    /** Same as [Render] with no extra modifier. */
    @Composable
    public fun Render(component: SduiComponent) {
        Render(component, Modifier)
    }

    /**
     * Renders [children] in order, each keyed by position and class so its `remember`ed state
     * survives a refetch with the same tree shape.
     *
     * [childModifier] hands down the scope-only part of the server modifier (`weight`, `alignment`),
     * which only the enclosing Row/Column can resolve.
     *
     * State does not follow a child through a server-side reorder: that needs a stable node id, which
     * the payload schema does not carry yet.
     */
    @Composable
    public fun RenderAll(
        children: List<SduiComponent>,
        childModifier: (SduiComponent) -> Modifier,
    )

    /** Same as [RenderAll] with no per-child modifier (no weight or alignment handed down). */
    @Composable
    public fun RenderAll(children: List<SduiComponent>) {
        RenderAll(children) { Modifier }
    }

    /**
     * Forwards [action] to the host: `onActionEvent` (with the component that owns this scope as the
     * source) and `onAction`, as given to [BlueprintRenderer].
     */
    public fun dispatch(action: SduiAction)
}
