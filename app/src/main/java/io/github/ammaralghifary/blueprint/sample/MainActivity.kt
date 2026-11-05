package io.github.ammaralghifary.blueprint.sample

import android.annotation.SuppressLint
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
@SuppressLint("UnsafeOptInUsageError")
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
    onDecodeError = { Log.w("Blueprint", "${it.kind} type=${it.type}: ${it.message}") },
    // One summary per decodeScreen call. The screen carries payload text, so log only its id and the count.
    onDecoded = { screen, errors -> Log.i("Blueprint", "decoded ${screen.id}: ${errors.size} problem(s)") },
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = blueprint.decodeScreen(SamplePayload.read(this))
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
