package io.github.ammaralghifary.blueprint.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.modifier.toComposeModifier
import io.github.ammaralghifary.blueprint.registry.SduiRegistry
import kotlinx.serialization.json.JsonObject

/** Renders [screen].body recursively through [blueprint]'s registry. */
@Composable
public fun BlueprintRenderer(
    screen: SduiScreen,
    blueprint: Blueprint,
    modifier: Modifier = Modifier,
    onAction: (SduiAction) -> Unit = {},
) {
    BlueprintRenderer(screen.body, blueprint, modifier, onAction)
}

/** Renders a single [component] subtree. */
@Composable
public fun BlueprintRenderer(
    component: SduiComponent,
    blueprint: Blueprint,
    modifier: Modifier = Modifier,
    onAction: (SduiAction) -> Unit = {},
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val scope = remember(blueprint) { RegistryScope(blueprint.registry) { currentOnAction(it) } }
    scope.Render(component, modifier)
}

private class RegistryScope(
    private val registry: SduiRegistry,
    private val onAction: (SduiAction) -> Unit,
) : SduiScope {

    @Composable
    override fun Render(component: SduiComponent, modifier: Modifier) {
        val mod = component.modifier?.toComposeModifier(modifier) ?: modifier
        val registration = registry.registrationFor(component)
        if (registration != null) {
            registration.Render(component, mod, this)
        } else {
            // A class nobody registered still needs something for the fallback to show
            val unknown = component as? UnknownComponent
                ?: UnknownComponent(
                    component::class.simpleName,
                    JsonObject(emptyMap()),
                    component.modifier
                )
            registry.fallback(unknown, mod)
        }
    }

    @Composable
    override fun RenderAll(children: List<SduiComponent>, childModifier: (SduiComponent) -> Modifier) {
        children.forEachIndexed { index, child ->
            // Position + class, see SduiScope.RenderAll
            key(index, child::class) { Render(child, childModifier(child)) }
        }
    }

    override fun dispatch(action: SduiAction) = onAction(action)
}
