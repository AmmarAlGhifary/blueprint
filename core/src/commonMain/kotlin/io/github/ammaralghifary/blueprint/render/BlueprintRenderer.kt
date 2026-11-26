package io.github.ammaralghifary.blueprint.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.modifier.toComposeModifier
import io.github.ammaralghifary.blueprint.registry.SduiRegistry
import kotlinx.serialization.json.JsonObject

/**
 * Renders [screen].body recursively through [blueprint]'s registry.
 *
 * Actions fired by components reach the host through [onActionEvent] (which also says which
 * component fired it) and [onAction] (the action alone). Both are called when both are given, so an
 * existing `onAction` keeps working; [onActionEvent] comes first in the list so a trailing lambda
 * still binds to [onAction].
 */
@Composable
public fun BlueprintRenderer(
    screen: SduiScreen,
    blueprint: Blueprint,
    modifier: Modifier = Modifier,
    onActionEvent: ((ActionEvent) -> Unit)? = null,
    onAction: (SduiAction) -> Unit = {},
) {
    BlueprintRenderer(screen.body, blueprint, modifier, onActionEvent, onAction)
}

/** Renders a single [component] subtree. Callbacks as for the [SduiScreen] overload. */
@Composable
public fun BlueprintRenderer(
    component: SduiComponent,
    blueprint: Blueprint,
    modifier: Modifier = Modifier,
    onActionEvent: ((ActionEvent) -> Unit)? = null,
    onAction: (SduiAction) -> Unit = {},
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val currentOnActionEvent by rememberUpdatedState(onActionEvent)
    val scope = remember(blueprint) {
        RegistryScope(
            blueprint.registry,
            onAction = { currentOnAction(it) },
            onActionEvent = { currentOnActionEvent?.invoke(it) },
        )
    }
    scope.renderNode(component, modifier)
}

/**
 * The tree-wide half: registry lookup and the host callbacks. Renderers only ever see a [NodeScope].
 *
 * @author Ammar Al Ghifary
 */
private class RegistryScope(
    private val registry: SduiRegistry,
    private val onAction: (SduiAction) -> Unit,
    private val onActionEvent: (ActionEvent) -> Unit,
) {

    @Composable
    fun renderNode(component: SduiComponent, modifier: Modifier) {
        val mod = component.modifier?.toComposeModifier(modifier) ?: modifier
        val registration = registry.registrationFor(component)
        if (registration != null) {
            // One scope per node position, so dispatch knows which component fired. The source is
            // refreshed on every composition: a refetch swaps the component but keeps the position.
            val scope = remember { NodeScope(this) }
            scope.source = component
            registration.Render(component, mod, scope)
        } else {
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
    fun renderChildren(children: List<SduiComponent>, childModifier: (SduiComponent) -> Modifier) {
        children.forEachIndexed { index, child ->
            key(index, child::class) { renderNode(child, childModifier(child)) }
        }
    }

    fun fire(action: SduiAction, source: SduiComponent) {
        onActionEvent(ActionEvent(action, source))
        onAction(action)
    }
}

/**
 * The [SduiScope] one registered component sees: rendering goes to the tree, dispatch is tagged with [source].
 *
 * @author Ammar Al Ghifary
 */
private class NodeScope(private val tree: RegistryScope) : SduiScope {
    lateinit var source: SduiComponent

    @Composable
    override fun Render(component: SduiComponent, modifier: Modifier) = tree.renderNode(component, modifier)

    @Composable
    override fun RenderAll(children: List<SduiComponent>, childModifier: (SduiComponent) -> Modifier) =
        tree.renderChildren(children, childModifier)

    override fun dispatch(action: SduiAction) = tree.fire(action, source)
}
