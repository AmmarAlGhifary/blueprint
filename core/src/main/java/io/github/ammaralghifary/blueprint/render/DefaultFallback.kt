package io.github.ammaralghifary.blueprint

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Placeholder for any component that could not be rendered as a typed one, whether its type is
 * unregistered or its fields did not fit the model. Debuggable builds show a labelled box (with
 * the decoding error when there is one). Release builds draw nothing, so users never see it;
 * replace it with `fallback { ... }` in [sduiRegistry] if you want a visible placeholder.
 */
internal val DefaultFallback: SduiFallback = { component, modifier ->
    val debuggable = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (debuggable) {
        Box(modifier.background(Color(0x22FF0000)).padding(8.dp)) {
            val label = "Unknown component: ${component.type ?: "<no type>"}"
            BasicText(if (component.error == null) label else "$label (invalid: ${component.error.take(120)})")
        }
    }
}
