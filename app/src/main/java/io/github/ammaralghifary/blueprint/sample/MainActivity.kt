package io.github.ammaralghifary.blueprint.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.defaults.DefaultSduiRegistry
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.registry.sduiRegistry
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import kotlinx.serialization.Serializable

/** A host-specific component, to show the stock pack and custom types living side by side. */
@Serializable
data class ScheduleCard(
    val title: String,
    val time: String,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

private val blueprint = Blueprint(
    sduiRegistry {
        include(DefaultSduiRegistry)
        component<ScheduleCard>("schedule_card") { c, mod, _ ->
            Column(mod.padding(8.dp)) {
                BasicText(c.title)
                BasicText(c.time)
            }
        }
    },
    // Tells the host what the server got wrong; here it just goes to logcat (tag "Blueprint").
    onDecodeError = { Log.w("Blueprint", "${it.kind} type=${it.type}: ${it.message}") },
    // One summary per decodeScreen call. The screen carries payload text, so log only its id and the count.
    onDecoded = { screen, errors -> Log.i("Blueprint", "decoded ${screen.id}: ${errors.size} problem(s)") },
)

// Two deliberate failures, each of which must stay local:
// - "hologram" is not a registered type
// - the "text" in the middle is missing its required `text` field
// Everything around them still renders.
private const val PAYLOAD = """
{
  "screen_id": "smoke",
  "body": {
    "type": "column",
    "modifier": { "padding": { "all": 16 } },
    "children": [
      { "type": "text", "text": "Hello from JSON", "style": "headline" },
      { "type": "text", "text": "Styled by the server", "modifier": { "text_color": "#00695C", "font_weight": 700 } },
      { "type": "hologram", "modifier": { "padding": { "vertical": 8 } } },
      { "type": "text", "modifier": { "padding": { "bottom": 8 } } },
      { "type": "divider" },
      {
        "type": "row",
        "modifier": { "width": { "type": "fill" }, "padding": { "vertical": 8 } },
        "children": [
          { "type": "text", "text": "weight 1",
            "modifier": { "weight": 1, "background_color": "#C8E6C9", "padding": { "all": 8 } } },
          { "type": "text", "text": "weight 2",
            "modifier": { "weight": 2, "background_color": "#BBDEFB", "padding": { "all": 8 } } }
        ]
      },
      { "type": "schedule_card", "title": "Standup", "time": "09:30" },
      { "type": "card", "action": { "type": "navigate", "destination": "details" },
        "children": [ { "type": "text", "text": "Tap this card", "modifier": { "padding": { "all": 12 } } } ] },
      { "type": "spacer", "size": 12 },
      { "type": "button", "text": "Filled", "action": { "type": "navigate", "destination": "home" } },
      { "type": "button", "text": "Disabled (no action)", "variant": "outlined" }
    ]
  }
}
"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = blueprint.decodeScreen(PAYLOAD)
        setContent {
            BlueprintRenderer(
                screen,
                blueprint,
                Modifier.safeDrawingPadding(),
                // Says which component fired the action; toString names classes only, so it is safe to log.
                onActionEvent = { Log.i("Blueprint", "action from $it") },
            )
        }
    }
}
