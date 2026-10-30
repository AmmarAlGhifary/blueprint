package io.github.ammaralghifary.blueprint.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.BlueprintRenderer
import io.github.ammaralghifary.blueprint.SduiComponent
import io.github.ammaralghifary.blueprint.SduiModifier
import io.github.ammaralghifary.blueprint.sduiRegistry
import kotlinx.serialization.Serializable

@Serializable
data class SampleText(val text: String, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class SampleColumn(val children: List<SduiComponent>, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class SampleRow(val children: List<SduiComponent>, override val modifier: SduiModifier? = null) : SduiComponent()

// Week 1 smoke test: three inline components. The `defaults` module replaces these in Week 2.
private val blueprint = Blueprint(
    sduiRegistry {
        component<SampleText>("text") { c, mod, _ -> BasicText(c.text, mod) }
        component<SampleColumn>("column") { c, mod, scope ->
            Column(mod) { scope.RenderAll(c.children) }
        }
        component<SampleRow>("row") { c, mod, scope ->
            Row(mod) {
                // `weight` is a RowScope extension, so toComposeModifier cannot apply it: the
                // container resolves it here and hands it down as each child's base modifier.
                scope.RenderAll(c.children) { child ->
                    child.modifier?.weight?.let { Modifier.weight(it) } ?: Modifier
                }
            }
        }
    },
)

// Two deliberate failures, each of which must stay local:
// - "hologram" is not a registered type
// - the third child is a registered "text" with its required `text` field missing
// Everything around them still renders.
private const val PAYLOAD = """
{
  "screen_id": "smoke",
  "body": {
    "type": "column",
    "modifier": { "padding": { "all": 16 } },
    "children": [
      { "type": "text", "text": "Hello from JSON" },
      { "type": "hologram", "modifier": { "padding": { "vertical": 8 } } },
      { "type": "text", "modifier": { "padding": { "bottom": 8 } } },
      {
        "type": "row",
        "modifier": { "width": { "type": "fill" }, "padding": { "bottom": 8 } },
        "children": [
          { "type": "text", "text": "weight 1",
            "modifier": { "weight": 1, "background_color": "#C8E6C9", "padding": { "all": 8 } } },
          { "type": "text", "text": "weight 2",
            "modifier": { "weight": 2, "background_color": "#BBDEFB", "padding": { "all": 8 } } }
        ]
      },
      { "type": "text", "text": "I still render after the unknown one",
        "modifier": { "background_color": "#FFE0B2", "padding": { "all": 8 }, "corner_radius": 8 } }
    ]
  }
}
"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = blueprint.decodeScreen(PAYLOAD)
        setContent {
            BlueprintRenderer(screen, blueprint, Modifier.safeDrawingPadding())
        }
    }
}
