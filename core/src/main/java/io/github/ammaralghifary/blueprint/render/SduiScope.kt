package io.github.ammaralghifary.blueprint

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Handed to every renderer so containers can recurse and components can fire actions. */
public interface SduiScope {
    /** Renders [component] through the registry (server modifier applied, fallback if unknown). */
    @Composable
    public fun Render(component: SduiComponent, modifier: Modifier = Modifier)

    /**
     * Renders [children] in order. Containers should call this instead of
     * `children.forEach { Render(it) }`.
     *
     * [childModifier] builds the base modifier for one child, which is how a container hands down
     * the parts of the server modifier that [toComposeModifier] cannot apply on its own: `weight`
     * and `alignment` are `RowScope`/`ColumnScope` extensions, so only the enclosing layout can
     * resolve them. The lambda runs in the container's scope, so:
     *
     * ```
     * Row(mod) {
     *     scope.RenderAll(c.children) { child ->
     *         child.modifier?.weight?.let { Modifier.weight(it) } ?: Modifier
     *     }
     * }
     * ```
     *
     * Each child composes inside its own `key` group, keyed by position and node class, which is
     * the weakest key that is always available. That keeps a child's `remember`ed state across a
     * refetch whose tree shape is unchanged, and drops it when a different component takes the
     * same slot. Surviving a server-side *reorder* needs a stable per-node id, which the payload
     * schema does not carry yet; when it does, this is the one place that has to change.
     */
    @Composable
    public fun RenderAll(
        children: List<SduiComponent>,
        childModifier: (SduiComponent) -> Modifier = { Modifier },
    )

    /** Forwards [action] to the `onAction` callback given to [BlueprintRenderer]. */
    public fun dispatch(action: SduiAction)
}
