package io.github.ammaralghifary.blueprint.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.render.BlueprintRenderer
import io.github.ammaralghifary.blueprint.SduiComponent
import io.github.ammaralghifary.blueprint.SduiModifier
import io.github.ammaralghifary.blueprint.sduiRegistry
import kotlinx.serialization.Serializable

@Serializable
data class SampleText(val text: String, override val modifier: SduiModifier? = null) : SduiComponent()

@Serializable
data class SampleColumn(val children: List<SduiComponent>, override val modifier: SduiModifier? = null) : SduiComponent()

// Week 1 smoke test: two inline components. The `defaults` module replaces these in Week 2.
private val blueprint = Blueprint(
    sduiRegistry {
        component<SampleText>("text") { c, mod, _ -> BasicText(c.text, mod) }
        component<SampleColumn>("column") { c, mod, scope ->
            Column(mod) { c.children.forEach { scope.Render(it) } }
        }
    },
)

// "hologram" is not registered: it must render the fallback box while its siblings still render.
private const val PAYLOAD = """
{
  "screen_id": "smoke",
  "body": {
    "type": "column",
    "modifier": { "padding": { "all": 16 } },
    "children": [
      { "type": "text", "text": "Hello from JSON" },
      { "type": "hologram", "modifier": { "padding": { "vertical": 8 } } },
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
