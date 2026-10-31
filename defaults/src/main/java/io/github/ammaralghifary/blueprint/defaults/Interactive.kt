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
import io.github.ammaralghifary.blueprint.SduiAction
import io.github.ammaralghifary.blueprint.SduiComponent
import io.github.ammaralghifary.blueprint.SduiModifier
import io.github.ammaralghifary.blueprint.SduiScope
import io.github.ammaralghifary.blueprint.toTextStyle
import kotlinx.serialization.Serializable

/** `button`: [variant] is `filled|outlined|text` (unknown → filled); disabled when [action] is null. */
@Serializable
public data class SduiButton(
    val text: String,
    val action: SduiAction? = null,
    val variant: String? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

/** `card`: a container that dispatches [action] when tapped. */
@Serializable
public data class SduiCard(
    val children: List<SduiComponent> = emptyList(),
    val action: SduiAction? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

@Composable
internal fun RenderButton(c: SduiButton, mod: Modifier, scope: SduiScope) {
    val onClick = { c.action?.let(scope::dispatch); Unit }
    val enabled = c.action != null
    val label: @Composable () -> Unit = {
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
    val clickable = if (action != null) mod.clickable { scope.dispatch(action) } else mod
    Card(clickable) { Column { scope.RenderAll(c.children) } }
}
