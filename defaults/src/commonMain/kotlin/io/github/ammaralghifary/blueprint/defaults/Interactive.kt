package io.github.ammaralghifary.blueprint.defaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.modifier.toTextStyle
import io.github.ammaralghifary.blueprint.render.SduiScope
import kotlinx.serialization.Serializable

/**
 * `button`: [variant] is `filled|outlined|text` (unknown → filled); disabled when [action] is null. While
 * [io.github.ammaralghifary.blueprint.render.LocalSduiActionContext] reports the action as running it is
 * disabled too, and shows a progress indicator if that lasts.
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class SduiButton(
    val text: String,
    val action: SduiAction? = null,
    val variant: String? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent

/**
 * `card`: a container that dispatches [action] when tapped, except while
 * [io.github.ammaralghifary.blueprint.render.LocalSduiActionContext] reports it as running.
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class SduiCard(
    val children: List<SduiComponent> = emptyList(),
    val action: SduiAction? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent

@Composable
internal fun RenderButton(c: SduiButton, mod: Modifier, scope: SduiScope) {
    val onClick = { c.action?.let(scope::dispatch); Unit }
    val running = isRunning(c.action)
    val enabled = c.action != null && !running
    val showProgress = showsProgress(c.action, running)
    val label: @Composable () -> Unit = {
        if (showProgress) ButtonProgress()
        Text(c.text, style = c.modifier.toTextStyle(MaterialTheme.typography.labelLarge))
    }
    when (c.variant) {
        "outlined" -> OutlinedButton(onClick, mod, enabled = enabled) { label() }
        "text" -> TextButton(onClick, mod, enabled = enabled) { label() }
        else -> Button(onClick, mod, enabled = enabled) { label() }
    }
}

@Composable
internal fun RenderCard(c: SduiCard, mod: Modifier, scope: SduiScope) {
    val action = c.action
    val enabled = !isRunning(action)
    val clickable = if (action != null) mod.clickable(enabled = enabled) { scope.dispatch(action) } else mod
    Card(clickable) { Column { scope.RenderAll(c.children) } }
}
