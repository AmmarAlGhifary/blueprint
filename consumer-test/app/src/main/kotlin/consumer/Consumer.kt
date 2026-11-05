package consumer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.defaults.DefaultSduiRegistry
import io.github.ammaralghifary.blueprint.model.ActionEvent
import io.github.ammaralghifary.blueprint.model.NavigateAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.model.SduiScreen
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import kotlinx.serialization.Serializable

// This file only has to compile. It is what a host app writes, so it touches every part of the public
// surface a Kotlin or Compose upgrade could break: a @Serializable model, the registry DSL, a composable
// renderer, SduiScope.Render and RenderAll called with their default arguments, dispatch, both
// callbacks, decodeScreen and BlueprintRenderer.

/** A component of the app's own: a model for the JSON... */
@Serializable
data class Banner(val text: String, override val modifier: SduiModifier? = null) : SduiComponent()

/** ...and a container, which has to render its children through the scope. */
@Serializable
data class Panel(
    val header: SduiComponent? = null,
    val children: List<SduiComponent> = emptyList(),
    val horizontal: Boolean = false,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

val blueprint = Blueprint(
    sduiRegistry {
        include(DefaultSduiRegistry)

        component<Banner>("banner") { c, mod, scope ->
            Text(c.text, mod.clickable { scope.dispatch(NavigateAction(destination = "banner")) })
        }

        component<Panel>("panel") { c, mod, scope ->
            Column(mod) {
                // A single slot, with the default modifier.
                c.header?.let { scope.Render(it) }
                if (c.horizontal) {
                    Row {
                        // The scope-only half of the server modifier is handed down by the container.
                        scope.RenderAll(c.children) { child ->
                            child.modifier?.weight?.let { Modifier.weight(it) } ?: Modifier
                        }
                    }
                } else {
                    // Every child, with the default childModifier.
                    scope.RenderAll(c.children)
                }
            }
        }
    },
    onDecodeError = { error -> println("${error.kind}: ${error.message}") },
    onDecoded = { screen, errors -> println("${screen.id}: ${errors.size} problem(s)") },
)

const val SAMPLE_PAYLOAD = """
{
  "screen_id": "consumer",
  "body": {
    "type": "panel",
    "header": { "type": "text", "text": "Consumer", "style": "headline" },
    "children": [
      { "type": "banner", "text": "Server-driven, native Compose" },
      { "type": "hologram" },
      { "type": "button", "text": "Continue", "action": { "type": "navigate", "destination": "home" } }
    ]
  }
}
"""

/** Decoding never throws; a payload that cannot be read comes back as an [UnknownComponent] body. */
fun decodeSample(json: String = SAMPLE_PAYLOAD): SduiScreen = blueprint.decodeScreen(json)

fun isReadable(screen: SduiScreen): Boolean = screen.body !is UnknownComponent

@Composable
fun ConsumerScreen(json: String = SAMPLE_PAYLOAD, onEvent: (ActionEvent) -> Unit = {}) {
    val screen = remember(json) { decodeSample(json) }
    BlueprintRenderer(screen, blueprint, onActionEvent = onEvent)
}

@Composable
fun ConsumerNode(component: SduiComponent) {
    BlueprintRenderer(component, blueprint, Modifier, onAction = { action -> println(action) })
}
