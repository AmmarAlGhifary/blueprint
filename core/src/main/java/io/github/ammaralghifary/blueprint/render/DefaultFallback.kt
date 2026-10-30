package io.github.ammaralghifary.blueprint

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Debuggable builds show a labelled box. Release builds draw nothing, so users never see it. */
internal val DefaultFallback: SduiFallback = { component, modifier ->
    val debuggable = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (debuggable) {
        Box(modifier.background(Color(0x22FF0000)).padding(8.dp)) {
            BasicText("Unknown component: ${component.type ?: "<no type>"}")
        }
    }
}
