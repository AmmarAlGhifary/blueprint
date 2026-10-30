package io.github.ammaralghifary.blueprint

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Handed to every renderer so containers can recurse and components can fire actions. */
public interface SduiScope {
    /** Renders [component] through the registry (server modifier applied, fallback if unknown). */
    @Composable
    public fun Render(component: SduiComponent, modifier: Modifier = Modifier)

    /** Forwards [action] to the `onAction` callback given to [io.github.ammaralghifary.blueprint.render.BlueprintRenderer]. */
    public fun dispatch(action: SduiAction)
}
