package io.github.ammaralghifary.blueprint.defaults

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ammaralghifary.blueprint.model.ActionStatus
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.render.LocalSduiActionContext
import kotlinx.coroutines.delay

// How a stock control shows that its action is running. Shared by the button, the card and the dialog.

// How long an action must have been running before a button shows it, so a quick one does not flicker.
private const val PROGRESS_DELAY_MS = 300L

/** False without an action, or when nothing tracks actions. */
@Composable
internal fun isRunning(action: SduiAction?): Boolean =
    action != null && LocalSduiActionContext.current?.statusOf(action) == ActionStatus.Running

/** True once [action] has been [running] for long enough to be worth showing. */
@Composable
internal fun showsProgress(action: SduiAction?, running: Boolean): Boolean {
    // The action the delay has elapsed for, so a control that changes its action starts the wait again.
    var slowAction by remember { mutableStateOf<SduiAction?>(null) }
    LaunchedEffect(running, action) {
        slowAction = null
        if (running) {
            delay(PROGRESS_DELAY_MS)
            slowAction = action
        }
    }
    return running && slowAction == action
}

/** The indicator a button puts before its label, in the label's colour. */
@Composable
internal fun ButtonProgress() {
    CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
    Spacer(Modifier.width(8.dp))
}
