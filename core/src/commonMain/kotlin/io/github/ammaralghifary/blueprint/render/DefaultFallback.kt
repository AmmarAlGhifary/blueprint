package io.github.ammaralghifary.blueprint.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.ammaralghifary.blueprint.registry.SduiFallback
import io.github.ammaralghifary.blueprint.registry.sduiRegistry

/**
 * Placeholder for any component that could not be rendered as a typed one, whether its type is
 * unregistered or its fields did not fit the model. Debug builds (see [isDebugBuild]) show a labelled
 * box (with the decoding error when there is one). Release builds draw nothing, so users never see it;
 * replace it with `fallback { ... }` in [sduiRegistry] if you want a visible placeholder.
 */
internal val DefaultFallback: SduiFallback = { component, modifier ->
    if (isDebugBuild()) {
        Box(modifier.background(Color(0x22FF0000)).padding(8.dp)) {
            val label = "Unknown component: ${component.type ?: "<no type>"}"
            BasicText(if (component.error == null) label else "$label (invalid: ${component.error.take(120)})")
        }
    }
}

/**
 * Whether the host app is a debug build: the debuggable flag on Android, a debug binary on iOS.
 * A desktop JVM has no such notion, so there it is always false.
 */
@Composable
internal expect fun isDebugBuild(): Boolean
